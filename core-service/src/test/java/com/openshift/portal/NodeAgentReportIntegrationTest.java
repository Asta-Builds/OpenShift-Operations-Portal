package com.openshift.portal;

import com.openshift.portal.acm.AcmHubClient;
import com.openshift.portal.acm.ClusterMetadata;
import com.openshift.portal.acm.ClusterObservation;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeAgentReport;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.domain.enums.ProviderType;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.LicenseWatermarkRepository;
import com.openshift.portal.repository.NodeAgentReportRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import com.openshift.portal.service.AcmCollectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A live hub reports clusters without nodes; the node agent in the cluster reports them. Runs the real endpoint,
 * collector, ingestion, licensing and inventory correlation, with only the hub's remote reads stubbed.
 */
@SpringBootTest(properties = "openshift.portal.simulator.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NodeAgentReportIntegrationTest {

    private static final String HUB = "hub-node-agent-it";

    @MockBean
    private AcmHubClient hubClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AcmCollectorService collectorService;
    @Autowired
    private AcmHubRepository hubRepository;
    @Autowired
    private ClusterRepository clusterRepository;
    @Autowired
    private ClusterSnapshotRepository snapshotRepository;
    @Autowired
    private NodeMetricsSnapshotRepository nodeRepository;
    @Autowired
    private NodeAgentReportRepository reportRepository;
    @Autowired
    private LicenseWatermarkRepository watermarkRepository;

    /** Clusters this hub reports; other contexts' hubs share the in-memory database and are answered with none. */
    private List<String> hubClusters = List.of();

    @BeforeEach
    void setUp() {
        if (hubRepository.findByName(HUB).isEmpty()) {
            hubRepository.save(AcmHub.builder().name(HUB).apiUrl("https://api.hub-node-agent-it.example.com:6443")
                    .credentialsSecretRef("hub-node-agent-it-credentials").build());
        }
        when(hubClient.fetchClusters(any())).thenAnswer(invocation -> {
            AcmHub hub = invocation.getArgument(0);
            return HUB.equals(hub.getName()) ? hubClusters.stream().map(NodeAgentReportIntegrationTest::withoutNodes).toList()
                    : List.of();
        });
    }

    @Test
    void agentNodesGiveLiveClustersTheirLicenseCoresAndInfrastructure() throws Exception {
        String cluster = "agent-it-prod";
        hubClusters = List.of(cluster);

        // The agent may start before any collection has seen the cluster
        mockMvc.perform(post("/node-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(report(cluster, "2026-09-27T18:00:00Z", """
                                {"name":"master-0","role":"MASTER","cpuCores":8,"memoryGb":32,"providerId":null},
                                {"name":"worker-0","role":"WORKER","cpuCores":16,"memoryGb":64.00,
                                 "providerId":"vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c"},
                                {"name":"worker-1","role":"WORKER","cpuCores":16,"memoryGb":64.00,
                                 "providerId":"vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5d"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterName").value(cluster))
                .andExpect(jsonPath("$.nodes").value(3))
                .andExpect(jsonPath("$.registered").value(false));

        collectorService.triggerCollection();

        ClusterSnapshot snapshot = latestSnapshot(cluster);
        assertThat(snapshot.getLicenseCoresCount()).isEqualTo(32);
        assertThat(snapshot.getTotalNodes()).isEqualTo(3);
        assertThat(snapshot.getWorkerNodes()).isEqualTo(2);
        List<NodeMetricsSnapshot> nodes = nodeRepository.findBySnapshotId(snapshot.getId()).stream()
                .sorted(Comparator.comparing(NodeMetricsSnapshot::getNodeName)).toList();
        assertThat(nodes).extracting(NodeMetricsSnapshot::getNodeName).containsExactly("master-0", "worker-0", "worker-1");
        assertThat(nodes).extracting(NodeMetricsSnapshot::getRole)
                .containsExactly(NodeRole.MASTER, NodeRole.WORKER, NodeRole.WORKER);
        assertThat(nodes.get(1).getProviderType()).isEqualTo(ProviderType.VSPHERE);
        assertThat(nodes.get(1).getMemoryGb()).isEqualByComparingTo("64.00");

        // Collections now record the day's license watermark, and the cluster counts in the audit
        assertThat(watermarkRepository.findByWatermarkDate(LocalDate.now()).orElseThrow().getPeakWorkerCores())
                .isGreaterThanOrEqualTo(32);
        mockMvc.perform(get("/licensing/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clustersWithoutNodeData[?(@.clusterName == '" + cluster + "')]").isEmpty());

        mockMvc.perform(get("/node-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.clusterName == '" + cluster + "')].registered").value(true))
                .andExpect(jsonPath("$[?(@.clusterName == '" + cluster + "')].fresh").value(true))
                .andExpect(jsonPath("$[?(@.clusterName == '" + cluster + "')].nodeCount").value(3));
    }

    @Test
    void staleReportIsNotUsedSoTheClusterShowsNoNodes() throws Exception {
        String cluster = "agent-it-stale";
        hubClusters = List.of(cluster);
        mockMvc.perform(post("/node-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(report(cluster, "2026-09-27T18:00:00Z", worker("worker-0", 16))))
                .andExpect(status().isOk());
        NodeAgentReport report = reportRepository.findById(cluster).orElseThrow();
        report.setReceivedAt(LocalDateTime.now().minusHours(2));
        reportRepository.save(report);

        collectorService.triggerCollection();

        assertThat(latestSnapshot(cluster).getTotalNodes()).isZero();
        assertThat(latestSnapshot(cluster).getLicenseCoresCount()).isZero();
        mockMvc.perform(get("/node-reports"))
                .andExpect(jsonPath("$[?(@.clusterName == '" + cluster + "')].fresh").value(false));
        // The audit lists it with the reason instead of counting it as 0 cores
        mockMvc.perform(get("/licensing/audit"))
                .andExpect(jsonPath("$.complianceStatus").value(org.hamcrest.Matchers.not("COMPLIANT")))
                .andExpect(jsonPath("$.clustersWithoutNodeData[?(@.clusterName == '" + cluster + "')].reason")
                        .value("STALE_AGENT_REPORT"));
        mockMvc.perform(get("/clusters"))
                .andExpect(jsonPath("$[?(@.clusterName == '" + cluster + "')].nodeDataAvailable").value(false));
        String csv = mockMvc.perform(get("/reports/export").param("type", "LICENSE_AUDIT"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.lines().filter(line -> line.startsWith("\"" + cluster + "\"")).findFirst().orElseThrow())
                .contains("\"\",\"\",").endsWith("\"MISSING\"");
    }

    @Test
    void delayedOlderReportDoesNotReplaceANewerOne() throws Exception {
        String cluster = "agent-it-order";
        mockMvc.perform(post("/node-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(report(cluster, "2026-09-27T18:05:00Z", worker("worker-0", 16) + "," + worker("worker-1", 16))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/node-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(report(cluster, "2026-09-27T18:00:00Z", worker("worker-0", 16))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").value(2));

        assertThat(reportRepository.findById(cluster).orElseThrow().getNodeCount()).isEqualTo(2);
    }

    @Test
    void malformedReportsAreRejected() throws Exception {
        mockMvc.perform(post("/node-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(report("Not A Cluster Name", "2026-09-27T18:00:00Z", worker("worker-0", 16))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/node-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(report("agent-it-bad", "2026-09-27T18:00:00Z",
                                "{\"name\":\"worker-0\",\"cpuCores\":16,\"memoryGb\":64}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/node-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(report("agent-it-bad", "2026-09-27T18:00:00Z",
                                "{\"name\":\"worker-0\",\"role\":\"WORKER\",\"cpuCores\":-1,\"memoryGb\":64}")))
                .andExpect(status().isBadRequest());

        // A running cluster always has nodes, so an empty report is broken rather than an empty cluster
        mockMvc.perform(post("/node-reports").contentType(MediaType.APPLICATION_JSON)
                        .content(report("agent-it-bad", "2026-09-27T18:00:00Z", "")))
                .andExpect(status().isBadRequest());

        assertThat(reportRepository.findById("agent-it-bad")).isEmpty();
    }

    private ClusterSnapshot latestSnapshot(String clusterName) {
        Cluster cluster = clusterRepository.findByClusterName(clusterName).orElseThrow();
        return snapshotRepository.findTopByClusterOrderBySnapshotTimestampDesc(cluster).orElseThrow();
    }

    /** What a live hub reports: capacity and metadata, but no nodes. */
    private static ClusterObservation withoutNodes(String clusterName) {
        return new ClusterObservation(clusterName, 40, BigDecimal.valueOf(12), BigDecimal.valueOf(160), BigDecimal.valueOf(48),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, "{}",
                new ClusterMetadata("production", "VSphere", "4.14.28", "eu-west"), null);
    }

    private static String report(String clusterName, String collectedAt, String nodes) {
        return """
                {"clusterName":"%s","agentVersion":"1.0.0","collectedAt":"%s","nodes":[%s]}
                """.formatted(clusterName, collectedAt, nodes);
    }

    private static String worker(String name, int cores) {
        return "{\"name\":\"%s\",\"role\":\"WORKER\",\"cpuCores\":%d,\"memoryGb\":64,\"providerId\":null}".formatted(name, cores);
    }
}
