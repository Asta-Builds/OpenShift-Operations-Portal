package com.openshift.portal.service;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.dto.LicenseAuditDto;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicensingServiceTest {

    @Mock
    private ClusterSnapshotRepository snapshotRepository;

    @Mock
    private NodeMetricsSnapshotRepository nodeMetricsRepository;

    @Mock
    private com.openshift.portal.repository.LicenseWatermarkRepository watermarkRepository;

    private AcmProperties properties;
    private LicensingService licensingService;

    @BeforeEach
    void setUp() {
        properties = new AcmProperties();
        properties.getLicensing().setDefaultCoreMultiplier(1.0);
        licensingService = new LicensingService(snapshotRepository, nodeMetricsRepository, watermarkRepository, properties);
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
}
