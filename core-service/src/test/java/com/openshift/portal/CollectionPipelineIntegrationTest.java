package com.openshift.portal;

import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.dto.ForecastingProjectionDto;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import com.openshift.portal.service.AcmCollectorService;
import com.openshift.portal.service.ForecastingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
    private AcmHubRepository acmHubRepository;
    @Autowired
    private ClusterRepository clusterRepository;
    @Autowired
    private ClusterSnapshotRepository snapshotRepository;
    @Autowired
    private NodeMetricsSnapshotRepository nodeMetricsRepository;
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
    void hubsEndpointListsHubsWithoutCredentials() throws Exception {
        mockMvc.perform(get("/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value((int) acmHubRepository.count()))
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].authToken").doesNotExist());
    }
}
