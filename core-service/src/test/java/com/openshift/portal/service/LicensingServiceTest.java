package com.openshift.portal.service;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.domain.entity.LicenseWatermark;
import com.openshift.portal.dto.LicenseAuditDto;
import com.openshift.portal.dto.LicenseAuditDto.ComplianceStatus;
import com.openshift.portal.dto.LicenseAuditDto.NodeDataGap;
import com.openshift.portal.dto.LicenseAuditDto.NodeDataGapReason;
import com.openshift.portal.dto.NodeAgentStatusDto;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicensingServiceTest {

    @Mock
    private ClusterSnapshotRepository snapshotRepository;

    @Mock
    private NodeMetricsSnapshotRepository nodeMetricsRepository;

    @Mock
    private com.openshift.portal.repository.LicenseWatermarkRepository watermarkRepository;

    @Mock
    private ClusterRepository clusterRepository;

    @Mock
    private NodeAgentReportService nodeAgentReports;

    private AcmProperties properties;
    private LicensingService licensingService;

    @BeforeEach
    void setUp() {
        properties = new AcmProperties();
        properties.getLicensing().setDefaultCoreMultiplier(1.0);
        licensingService = new LicensingService(snapshotRepository, nodeMetricsRepository, watermarkRepository,
                clusterRepository, nodeAgentReports, properties);
    }

    @Test
    void calculateLicenseCores_onlyCountsWorkerNodes() {
        Cluster cluster = Cluster.builder().id(UUID.randomUUID()).build();
        LocalDateTime now = LocalDateTime.now();

        NodeMetricsSnapshot masterNode = NodeMetricsSnapshot.builder()
                .cluster(cluster)
                .nodeName("master-1")
                .role(NodeRole.MASTER)
                .cpuCores(8)
                .build();

        NodeMetricsSnapshot worker1 = NodeMetricsSnapshot.builder()
                .cluster(cluster)
                .nodeName("worker-1")
                .role(NodeRole.WORKER)
                .cpuCores(16)
                .build();

        NodeMetricsSnapshot worker2 = NodeMetricsSnapshot.builder()
                .cluster(cluster)
                .nodeName("worker-2")
                .role(NodeRole.WORKER)
                .cpuCores(16)
                .build();

        int cores = licensingService.calculateLicenseCores(List.of(masterNode, worker1, worker2), InfrastructureType.VMWARE);

        // 16 + 16 = 32 worker cores (master node excluded)
        assertThat(cores).isEqualTo(32);
    }

    @Test
    void calculateLicenseCores_returnsZeroForEmptyNodes() {
        int cores = licensingService.calculateLicenseCores(List.of(), InfrastructureType.BARE_METAL);
        assertThat(cores).isEqualTo(0);
    }

    @Test
    void generateLicenseAudit_aggregatesAcrossClustersCorrectly() {
        Team teamA = Team.builder().name("Platform Team").build();
        Cluster cluster1 = Cluster.builder()
                .id(UUID.randomUUID())
                .clusterName("ocp-prod-01")
                .environment(Environment.PRODUCTION)
                .ownerTeam(teamA)
                .infrastructureType(InfrastructureType.BARE_METAL)
                .build();

        Cluster cluster2 = Cluster.builder()
                .id(UUID.randomUUID())
                .clusterName("ocp-dev-01")
                .environment(Environment.DEVELOPMENT)
                .ownerTeam(teamA)
                .infrastructureType(InfrastructureType.VMWARE)
                .build();

        ClusterSnapshot snap1 = ClusterSnapshot.builder()
                .cluster(cluster1)
                .workerNodes(4)
                .totalNodes(7)
                .licenseCoresCount(64)
                .build();

        ClusterSnapshot snap2 = ClusterSnapshot.builder()
                .cluster(cluster2)
                .workerNodes(2)
                .totalNodes(5)
                .licenseCoresCount(16)
                .build();

        when(snapshotRepository.findLatestSnapshotsForAllClusters()).thenReturn(List.of(snap1, snap2));

        LicenseAuditDto audit = licensingService.generateLicenseAudit();

        assertThat(audit.getTotalLicenseCores()).isEqualTo(80);
        assertThat(audit.getWorkerNodesCount()).isEqualTo(6);
        assertThat(audit.getMasterNodesCount()).isEqualTo(6); // (7-4) + (5-2)
        assertThat(audit.getBareMetalCores()).isEqualTo(64);
        assertThat(audit.getVirtualCores()).isEqualTo(16);
        assertThat(audit.getCoresByEnvironment().get("PRODUCTION")).isEqualTo(64);
        assertThat(audit.getCoresByEnvironment().get("DEVELOPMENT")).isEqualTo(16);
        assertThat(audit.getCoresByOwnerTeam().get("Platform Team")).isEqualTo(80);
    }

    @Test
    void generateLicenseAudit_listsClustersWithoutNodeDataInsteadOfCountingThemAsZero() {
        Team team = Team.builder().name("Platform Team").build();
        Cluster counted = cluster("ocp-counted", team);
        Cluster noAgent = cluster("ocp-no-agent", Team.builder().name("Payments").build());
        Cluster stale = cluster("ocp-stale", team);
        Cluster awaiting = cluster("ocp-awaiting", team);
        Cluster neverCollected = cluster("ocp-never-collected", team);
        LocalDateTime received = LocalDateTime.now().minusHours(3);
        when(snapshotRepository.findLatestSnapshotsForAllClusters()).thenReturn(List.of(
                snapshot(counted, 7, 4, 64), snapshot(noAgent, 0, 0, 0), snapshot(stale, 0, 0, 0), snapshot(awaiting, 0, 0, 0)));
        when(clusterRepository.findAll()).thenReturn(List.of(counted, noAgent, stale, awaiting, neverCollected));
        when(nodeAgentReports.statusesByCluster()).thenReturn(Map.of(
                "ocp-stale", agentStatus("ocp-stale", received, false),
                "ocp-awaiting", agentStatus("ocp-awaiting", received, true)));

        LicenseAuditDto audit = licensingService.generateLicenseAudit();

        assertThat(audit.getComplianceStatus()).isEqualTo(ComplianceStatus.INCOMPLETE);
        assertThat(audit.isComplianceBreach()).isFalse();
        assertThat(audit.getTotalLicenseCores()).isEqualTo(64);
        assertThat(audit.getClustersCounted()).isEqualTo(1);
        assertThat(audit.getClustersWithoutNodeData()).containsExactly(
                new NodeDataGap("ocp-awaiting", "PRODUCTION", NodeDataGapReason.AWAITING_COLLECTION, received),
                new NodeDataGap("ocp-never-collected", "PRODUCTION", NodeDataGapReason.NOT_COLLECTED, null),
                new NodeDataGap("ocp-no-agent", "PRODUCTION", NodeDataGapReason.NO_AGENT_REPORT, null),
                new NodeDataGap("ocp-stale", "PRODUCTION", NodeDataGapReason.STALE_AGENT_REPORT, received));
        // A team whose clusters all lack node data is not shown as owning 0 cores
        assertThat(audit.getCoresByOwnerTeam()).containsOnly(Map.entry("Platform Team", 64));
    }

    @Test
    void generateLicenseAudit_knownCoresAboveTheCapAreABreachEvenWithMissingClusters() {
        properties.getLicensing().setLicensedCapCores(100);
        Cluster big = cluster("ocp-big", null);
        Cluster unknown = cluster("ocp-unknown", null);
        when(snapshotRepository.findLatestSnapshotsForAllClusters()).thenReturn(List.of(
                snapshot(big, 10, 8, 128), snapshot(unknown, 0, 0, 0)));

        LicenseAuditDto audit = licensingService.generateLicenseAudit();

        assertThat(audit.getComplianceStatus()).isEqualTo(ComplianceStatus.BREACH);
        assertThat(audit.isComplianceBreach()).isTrue();
        assertThat(audit.getLicensedCapCores()).isEqualTo(100);
    }

    @Test
    void generateLicenseAudit_highWatermarkIsTheHighestDailyPeakOfThePeriod() {
        Cluster cluster = cluster("ocp-prod", null);
        when(snapshotRepository.findLatestSnapshotsForAllClusters()).thenReturn(List.of(snapshot(cluster, 5, 3, 80)));
        when(watermarkRepository.findPeakSince(LocalDate.now().minusDays(365))).thenReturn(620);

        LicenseAuditDto audit = licensingService.generateLicenseAudit();

        assertThat(audit.getTotalLicenseCores()).isEqualTo(80);
        assertThat(audit.getHighWatermarkCores()).isEqualTo(620);
        assertThat(audit.getComplianceStatus()).isEqualTo(ComplianceStatus.BREACH);
    }

    @Test
    void recordDailyWatermark_createsTodaysRowFromTheCurrentCores() {
        when(snapshotRepository.sumLatestLicenseCoresCount()).thenReturn(96);
        when(watermarkRepository.findByWatermarkDate(LocalDate.now())).thenReturn(Optional.empty());
        when(watermarkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LicenseWatermark watermark = licensingService.recordDailyWatermark();

        assertThat(watermark.getWatermarkDate()).isEqualTo(LocalDate.now());
        assertThat(watermark.getPeakWorkerCores()).isEqualTo(96);
        assertThat(watermark.getLicensedCapCores()).isEqualTo(500);
        assertThat(watermark.getComplianceBreach()).isFalse();
    }

    @Test
    void recordDailyWatermark_keepsTheDaysHighestValue() {
        properties.getLicensing().setLicensedCapCores(110);
        LicenseWatermark today = LicenseWatermark.builder().watermarkDate(LocalDate.now()).peakWorkerCores(120)
                .licensedCapCores(110).build();
        when(watermarkRepository.findByWatermarkDate(LocalDate.now())).thenReturn(Optional.of(today));
        when(watermarkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(snapshotRepository.sumLatestLicenseCoresCount()).thenReturn(100);
        assertThat(licensingService.recordDailyWatermark().getPeakWorkerCores()).isEqualTo(120);

        when(snapshotRepository.sumLatestLicenseCoresCount()).thenReturn(150);
        LicenseWatermark raised = licensingService.recordDailyWatermark();
        assertThat(raised.getPeakWorkerCores()).isEqualTo(150);
        assertThat(raised.getComplianceBreach()).isTrue();
    }

    private static Cluster cluster(String name, Team team) {
        return Cluster.builder().id(UUID.randomUUID()).clusterName(name).environment(Environment.PRODUCTION)
                .ownerTeam(team).infrastructureType(InfrastructureType.VMWARE).build();
    }

    private static ClusterSnapshot snapshot(Cluster cluster, int totalNodes, int workerNodes, int licenseCores) {
        return ClusterSnapshot.builder().cluster(cluster).totalNodes(totalNodes).workerNodes(workerNodes)
                .licenseCoresCount(licenseCores).build();
    }

    private static NodeAgentStatusDto agentStatus(String clusterName, LocalDateTime receivedAt, boolean fresh) {
        return new NodeAgentStatusDto(clusterName, "1.0.0", receivedAt, receivedAt, 3, true, fresh);
    }
}
