package com.openshift.portal;

import com.openshift.portal.acm.HubResilience;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.dto.AttributionReportDto;
import com.openshift.portal.dto.ForecastingProjectionDto;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import com.openshift.portal.service.AcmCollectorService;
import com.openshift.portal.service.AttributionService;
import com.openshift.portal.service.ForecastingService;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the collector the way the scheduler does (outside any web request) against the seeded simulator fleet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CollectionPipelineIntegrationTest {

    @Autowired
    private AcmCollectorService collectorService;
    @Autowired
    private ForecastingService forecastingService;
    @Autowired
    private AttributionService attributionService;
    @Autowired
    private AcmHubRepository acmHubRepository;
    @Autowired
    private ClusterRepository clusterRepository;
    @Autowired
    private ClusterSnapshotRepository snapshotRepository;
    @Autowired
    private NodeMetricsSnapshotRepository nodeMetricsRepository;
    @Autowired
    private RetryRegistry retryRegistry;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void repeatedCollectionsKeepDailyTotalsAndPersistNodes() throws Exception {
        for (int i = 0; i < 3; i++) {
            SnapshotTriggerResultDto result = collectorService.triggerCollection();
            assertThat(result.getClustersProcessed()).isEqualTo(clusterRepository.count());
        }

        List<ClusterSnapshot> latest = snapshotRepository.findLatestSnapshotsForAllClusters();
        int latestAllocatedCores = latest.stream().mapToInt(ClusterSnapshot::getAllocatedCpuCores).sum();

        ForecastingProjectionDto projection = forecastingService.generateProjection(30, null);
        assertThat(projection.isInsufficientData()).isFalse();
        assertThat(projection.getCurrentCores()).isEqualTo(latestAllocatedCores);

        for (ClusterSnapshot snapshot : latest) {
            assertThat(nodeMetricsRepository.findBySnapshotId(snapshot.getId())).hasSize(snapshot.getTotalNodes());
        }
        mockMvc.perform(get("/clusters/{id}", latest.get(0).getCluster().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeMetrics.length()").value(latest.get(0).getTotalNodes()));
    }

    @Test
    void hubsEndpointReportsSyncStateWithoutCredentials() throws Exception {
        collectorService.triggerCollection();

        mockMvc.perform(get("/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value((int) acmHubRepository.count()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].consecutiveFailures").value(0))
                .andExpect(jsonPath("$[0].circuitBreakerState").value("CLOSED"))
                .andExpect(jsonPath("$[0].latestSyncRun.status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].latestSyncRun.attempts").value(1))
                .andExpect(jsonPath("$[0].authToken").doesNotExist());
    }

    @Test
    void simulatedNamespacesAttributeByOwnerLabelAndReconcile() throws Exception {
        collectorService.triggerCollection();

        AttributionReportDto report = attributionService.attribute(LocalDate.now(), LocalDate.now(), null);

        // Team name slug, alias and cost-center label all resolve; the unknown owner and unlabelled namespaces do not
        assertThat(report.getTeams()).extracting(AttributionReportDto.Row::getTeamName)
                .contains("Payments Platform", "Digital Channels", "Data & AI Analytics");
        assertThat(report.getUnmappedOwners()).extracting(AttributionReportDto.UnmappedOwner::getOwnerLabelValue)
                .containsExactly("core-banking");
        assertThat(report.getUnattributed().getCpuRequestCores()).isPositive();
        assertThat(report.getTotal().getCpuRequestCores()).isEqualByComparingTo(report.getClusterCpuRequestCores());
        assertThat(report.getTotal().getMemoryRequestGb()).isEqualByComparingTo(report.getClusterMemoryRequestGb());

        Cluster cluster = clusterRepository.findByClusterName("ocp-prod-eu-west-01").orElseThrow();
        mockMvc.perform(get("/clusters/{id}", cluster.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerLabelKey").value("openshift.io/owner-team"))
                .andExpect(jsonPath("$.namespaces[?(@.namespaceName == 'openshift-monitoring')].ownerTeamName").value("Unattributed"))
                .andExpect(jsonPath("$.namespaces[?(@.namespaceName == 'legacy-batch')].ownerLabelValue").value("core-banking"))
                .andExpect(jsonPath("$.namespaces[?(@.namespaceName == 'data-pipeline')].ownerTeamName").value("Data & AI Analytics"));
        mockMvc.perform(get("/attribution/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unattributed.teamName").value("Unattributed"));
    }

    @Test
    void retryBackoffDoublesFromTwoSeconds() {
        RetryConfig config = retryRegistry.getConfiguration(HubResilience.CONFIG).orElseThrow();

        assertThat(config.getMaxAttempts()).isEqualTo(3);
        assertThat(config.getIntervalBiFunction().apply(1, null)).isEqualTo(2000L);
        assertThat(config.getIntervalBiFunction().apply(2, null)).isEqualTo(4000L);
    }
}
