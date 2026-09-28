package com.openshift.portal.service;

import com.openshift.portal.acm.AcmHubClient;
import com.openshift.portal.acm.ClusterMetadata;
import com.openshift.portal.acm.ClusterObservation;
import com.openshift.portal.acm.HubResilience;
import com.openshift.portal.acm.NodeObservation;
import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.HubSyncRun;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.domain.enums.SyncStatus;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.exception.AcmConnectionException;
import com.openshift.portal.notification.AlertService;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.HubSyncRunRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcmCollectorServiceTest {

    @Mock
    private AcmHubRepository acmHubRepository;
    @Mock
    private ClusterRepository clusterRepository;
    @Mock
    private HubSyncRunRepository syncRunRepository;
    @Mock
    private SnapshotIngestionService ingestionService;
    @Mock
    private ObjectProvider<AcmHubClient> hubClientProvider;
    @Mock
    private AcmHubClient hubClient;
    /** Answers Optional.empty() unless stubbed: no agent reports. */
    @Mock
    private NodeAgentReportService nodeAgentReports;
    @Mock
    private LicensingService licensingService;
    @Mock
    private AlertService alertService;

    private AcmHub hub;
    private Cluster clusterA;
    private Cluster clusterB;
    private AcmCollectorService collectorService;

    @BeforeEach
    void setUp() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(AcmConnectionException.class)
                .build();
        CircuitBreakerConfig breakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .build();
        HubResilience hubResilience = new HubResilience(
                CircuitBreakerRegistry.of(Map.of(HubResilience.CONFIG, breakerConfig)),
                RetryRegistry.of(Map.of(HubResilience.CONFIG, retryConfig)));

        collectorService = new AcmCollectorService(acmHubRepository, clusterRepository, syncRunRepository,
                ingestionService, new ClusterDiscovery(), hubClientProvider, hubResilience, new AcmProperties(),
                nodeAgentReports, licensingService, alertService);

        hub = AcmHub.builder().id(UUID.randomUUID()).name("hub-test").apiUrl("https://hub.example.com").build();
        clusterA = cluster("ocp-a");
        clusterB = cluster("ocp-b");
    }

    @Test
    void successfulSync_ingestsEveryClusterAndRecordsSuccess() {
        givenHubWithClusters();
        when(hubClient.fetchClusters(hub)).thenReturn(List.of(observation("ocp-a"), observation("ocp-b")));

        SnapshotTriggerResultDto result = collectorService.triggerCollection();

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getClustersProcessed()).isEqualTo(2);
        assertThat(result.getSnapshotsCreated()).isEqualTo(2);
        verify(ingestionService).ingest(eq(clusterA), any(), any(), eq(true));
        verify(ingestionService).ingest(eq(clusterB), any(), any(), eq(true));

        HubSyncRun run = savedRun();
        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getAttempts()).isEqualTo(1);
        assertThat(run.getClustersOk()).isEqualTo(2);
        assertThat(hub.getStatus()).isEqualTo(HubStatus.ACTIVE);
        assertThat(hub.getConsecutiveFailures()).isZero();
        assertThat(hub.getLastSyncTimestamp()).isNotNull();
    }

    @Test
    void transportFailure_isRetriedAndThenSucceeds() {
        givenHubWithClusters();
        when(hubClient.fetchClusters(hub))
                .thenThrow(new AcmConnectionException("connect timed out"))
                .thenReturn(List.of(observation("ocp-a"), observation("ocp-b")));

        collectorService.triggerCollection();

        HubSyncRun run = savedRun();
        assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(run.getAttempts()).isEqualTo(2);
    }

    @Test
    void persistentTransportFailure_failsAfterAllAttemptsAndMarksHubUnreachable() {
        when(hubClientProvider.getIfAvailable()).thenReturn(hubClient);
        when(acmHubRepository.findAll(any(Sort.class))).thenReturn(List.of(hub));
        when(syncRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hubClient.fetchClusters(hub)).thenThrow(new AcmConnectionException("connection refused"));

        SnapshotTriggerResultDto result = collectorService.triggerCollection();

        assertThat(result.getStatus()).isEqualTo("PARTIAL");
        HubSyncRun run = savedRun();
        assertThat(run.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(run.getAttempts()).isEqualTo(3);
        assertThat(run.getErrorMessage()).isEqualTo("connection refused");
        assertThat(hub.getStatus()).isEqualTo(HubStatus.UNREACHABLE);
        assertThat(hub.getConsecutiveFailures()).isEqualTo(1);
        verifyNoInteractions(ingestionService);
    }

    @Test
    void nonTransportFailure_isNotRetriedAndMarksHubInError() {
        when(hubClientProvider.getIfAvailable()).thenReturn(hubClient);
        when(acmHubRepository.findAll(any(Sort.class))).thenReturn(List.of(hub));
        when(syncRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // e.g. an expired token: retrying cannot help
        when(hubClient.fetchClusters(hub)).thenThrow(new IllegalStateException("401 Unauthorized"));

        collectorService.triggerCollection();

        HubSyncRun run = savedRun();
        assertThat(run.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(run.getAttempts()).isEqualTo(1);
        assertThat(hub.getStatus()).isEqualTo(HubStatus.ERROR);
    }

    @Test
    void failedClusterObservation_marksRunPartialAndHubDegraded() {
        givenHubWithClusters();
        when(hubClient.fetchClusters(hub)).thenReturn(List.of(
                observation("ocp-a"),
                ClusterObservation.failed("ocp-b", "metrics query timed out")));

        collectorService.triggerCollection();

        HubSyncRun run = savedRun();
        assertThat(run.getStatus()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(run.getClustersOk()).isEqualTo(1);
        assertThat(run.getClustersFailed()).isEqualTo(1);
        assertThat(hub.getStatus()).isEqualTo(HubStatus.DEGRADED);
        // Some data arrived, so the hub is not counted as failing
        assertThat(hub.getConsecutiveFailures()).isZero();
        verify(ingestionService, times(1)).ingest(any(), any(), any(), anyBoolean());
    }

    @Test
    void clusterReportedWithMetadata_isRegisteredAndCollected() {
        when(hubClientProvider.getIfAvailable()).thenReturn(hubClient);
        when(acmHubRepository.findAll(any(Sort.class))).thenReturn(List.of(hub));
        when(clusterRepository.findByAcmHubId(hub.getId())).thenReturn(List.of());
        when(clusterRepository.save(any(Cluster.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(syncRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ClusterObservation discovered = new ClusterObservation("prod-east", 48, BigDecimal.valueOf(30), BigDecimal.valueOf(187),
                BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, "{}",
                new ClusterMetadata("production", "AWS", "4.14.28", "us-east-1"), null);
        when(hubClient.fetchClusters(hub)).thenReturn(List.of(discovered));

        collectorService.triggerCollection();

        ArgumentCaptor<Cluster> registered = ArgumentCaptor.forClass(Cluster.class);
        verify(clusterRepository).save(registered.capture());
        assertThat(registered.getValue().getClusterName()).isEqualTo("prod-east");
        assertThat(registered.getValue().getAcmHub()).isSameAs(hub);
        assertThat(registered.getValue().getEnvironment()).isEqualTo(Environment.PRODUCTION);
        assertThat(registered.getValue().getInfrastructureType()).isEqualTo(InfrastructureType.AWS);
        verify(ingestionService).ingest(eq(registered.getValue()), eq(discovered), any(), eq(true));
        assertThat(savedRun().getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void clusterWithoutNodes_getsTheNodesOfItsAgentReport() {
        givenHubWithClusters();
        when(hubClient.fetchClusters(hub)).thenReturn(List.of(observation("ocp-a"), observation("ocp-b")));
        List<NodeObservation> reported = List.of(
                new NodeObservation("ocp-a-worker-0", NodeRole.WORKER, 16, BigDecimal.valueOf(64), "vsphere://a0"));
        when(nodeAgentReports.freshNodes("ocp-a")).thenReturn(Optional.of(reported));
        when(nodeAgentReports.freshNodes("ocp-b")).thenReturn(Optional.empty());

        collectorService.triggerCollection();

        ArgumentCaptor<ClusterObservation> ingestedA = ArgumentCaptor.forClass(ClusterObservation.class);
        verify(ingestionService).ingest(eq(clusterA), ingestedA.capture(), any(), eq(true));
        assertThat(ingestedA.getValue().nodes()).isEqualTo(reported);
        assertThat(ingestedA.getValue().totalCpuCores()).isEqualTo(100);
        // No fresh report for ocp-b: it is stored without nodes, as before
        ArgumentCaptor<ClusterObservation> ingestedB = ArgumentCaptor.forClass(ClusterObservation.class);
        verify(ingestionService).ingest(eq(clusterB), ingestedB.capture(), any(), eq(true));
        assertThat(ingestedB.getValue().nodes()).isEmpty();
    }

    @Test
    void nodesReportedByTheHubClientAreKept() {
        givenHubWithClusters();
        List<NodeObservation> simulated = List.of(
                new NodeObservation("ocp-a-worker-0", NodeRole.WORKER, 8, BigDecimal.valueOf(32), null));
        when(hubClient.fetchClusters(hub)).thenReturn(List.of(observation("ocp-a").withNodes(simulated)));

        collectorService.triggerCollection();

        ArgumentCaptor<ClusterObservation> ingested = ArgumentCaptor.forClass(ClusterObservation.class);
        verify(ingestionService).ingest(eq(clusterA), ingested.capture(), any(), eq(true));
        assertThat(ingested.getValue().nodes()).isEqualTo(simulated);
        verify(nodeAgentReports, never()).freshNodes(any());
    }

    @Test
    void collectionThatStoresSnapshots_updatesTodaysLicenseWatermark() {
        givenHubWithClusters();
        when(hubClient.fetchClusters(hub)).thenReturn(List.of(observation("ocp-a"), observation("ocp-b")));

        collectorService.triggerCollection();

        verify(licensingService).recordDailyWatermark();
        verify(alertService).evaluate();
    }

    @Test
    void collectionWithoutSnapshots_leavesTheWatermarkAlone() {
        when(hubClientProvider.getIfAvailable()).thenReturn(hubClient);
        when(acmHubRepository.findAll(any(Sort.class))).thenReturn(List.of(hub));
        when(syncRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hubClient.fetchClusters(hub)).thenThrow(new IllegalStateException("401 Unauthorized"));

        collectorService.triggerCollection();

        verify(licensingService, never()).recordDailyWatermark();
        verify(alertService, never()).evaluate();
    }

    @Test
    void failedWatermarkUpdate_doesNotFailTheCollection() {
        givenHubWithClusters();
        when(hubClient.fetchClusters(hub)).thenReturn(List.of(observation("ocp-a"), observation("ocp-b")));
        when(licensingService.recordDailyWatermark()).thenThrow(new IllegalStateException("duplicate watermark date"));

        SnapshotTriggerResultDto result = collectorService.triggerCollection();

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getSnapshotsCreated()).isEqualTo(2);
    }

    @Test
    void failedAlertEvaluation_doesNotFailTheCollection() {
        givenHubWithClusters();
        when(hubClient.fetchClusters(hub)).thenReturn(List.of(observation("ocp-a"), observation("ocp-b")));
        doThrow(new IllegalStateException("template missing")).when(alertService).evaluate();

        SnapshotTriggerResultDto result = collectorService.triggerCollection();

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void missingClient_skipsCollection() {
        when(hubClientProvider.getIfAvailable()).thenReturn(null);

        SnapshotTriggerResultDto result = collectorService.triggerCollection();

        assertThat(result.getStatus()).isEqualTo("SKIPPED");
        verifyNoInteractions(acmHubRepository, syncRunRepository, ingestionService);
    }

    private void givenHubWithClusters() {
        when(hubClientProvider.getIfAvailable()).thenReturn(hubClient);
        when(acmHubRepository.findAll(any(Sort.class))).thenReturn(List.of(hub));
        when(clusterRepository.findByAcmHubId(hub.getId())).thenReturn(List.of(clusterA, clusterB));
        when(syncRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private HubSyncRun savedRun() {
        ArgumentCaptor<HubSyncRun> runCaptor = ArgumentCaptor.forClass(HubSyncRun.class);
        verify(syncRunRepository).save(runCaptor.capture());
        verify(acmHubRepository).save(hub);
        return runCaptor.getValue();
    }

    private Cluster cluster(String name) {
        return Cluster.builder()
                .id(UUID.randomUUID())
                .clusterName(name)
                .acmHub(hub)
                .environment(Environment.PRODUCTION)
                .infrastructureType(InfrastructureType.BARE_METAL)
                .build();
    }

    private static ClusterObservation observation(String clusterName) {
        return new ClusterObservation(clusterName, 100, BigDecimal.valueOf(60), BigDecimal.valueOf(400), BigDecimal.valueOf(240),
                BigDecimal.valueOf(4000), BigDecimal.valueOf(2000), List.of(), null, "{}", null, null);
    }
}
