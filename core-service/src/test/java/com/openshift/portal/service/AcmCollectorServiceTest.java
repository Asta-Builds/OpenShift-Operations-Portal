package com.openshift.portal.service;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcmCollectorServiceTest {

    @Mock
    private AcmHubRepository acmHubRepository;
    @Mock
    private ClusterRepository clusterRepository;
    @Mock
    private ClusterSnapshotRepository snapshotRepository;
    @Mock
    private NodeMetricsSnapshotRepository nodeMetricsRepository;
    @Mock
    private LicensingService licensingService;
    @Mock
    private AcmSimulatorService simulatorService;

    private AcmProperties properties;
    private AcmCollectorService collectorService;

    @BeforeEach
    void setUp() {
        properties = new AcmProperties();
        properties.getSimulator().setEnabled(false);
        collectorService = new AcmCollectorService(
                acmHubRepository,
                clusterRepository,
                snapshotRepository,
                nodeMetricsRepository,
                licensingService,
                simulatorService,
                properties
        );
    }

    @Test
    void triggerCollection_processesHubsAndCreatesSnapshots() {
        UUID hubId = UUID.randomUUID();
        AcmHub hub = AcmHub.builder()
                .id(hubId)
                .name("hub-test")
                .apiUrl("https://hub.example.com")
                .status(HubStatus.ACTIVE)
                .build();

        Cluster cluster = Cluster.builder()
                .id(UUID.randomUUID())
                .clusterName("ocp-test-01")
                .acmHub(hub)
                .environment(Environment.PRODUCTION)
                .infrastructureType(InfrastructureType.BARE_METAL)
                .build();
        hub.setClusters(List.of(cluster));

        when(acmHubRepository.findAll()).thenReturn(List.of(hub));
        when(clusterRepository.findAll()).thenReturn(List.of(cluster));
        when(licensingService.calculateLicenseCores(any(), any())).thenReturn(64);

        SnapshotTriggerResultDto result = collectorService.triggerCollection();

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getSnapshotsCreated()).isEqualTo(1);
        verify(snapshotRepository, times(1)).save(any());
        verify(acmHubRepository, times(1)).save(hub);
    }

    @Test
    void collectionFallback_marksHubAsUnreachable() {
        AcmHub hub = AcmHub.builder()
                .id(UUID.randomUUID())
                .name("hub-failed")
                .status(HubStatus.ACTIVE)
                .build();

        int result = collectorService.collectionFallback(hub, new RuntimeException("Connection timed out"));

        assertThat(result).isEqualTo(0);
        assertThat(hub.getStatus()).isEqualTo(HubStatus.UNREACHABLE);
        verify(acmHubRepository, times(1)).save(hub);
    }
}
