package com.openshift.portal.service;

import com.openshift.portal.acm.AcmHubClient;
import com.openshift.portal.acm.ClusterObservation;
import com.openshift.portal.acm.HubResilience;
import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.HubSyncRun;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.SyncStatus;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.exception.AcmConnectionException;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.HubSyncRunRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcmCollectorService {

    private final AcmHubRepository acmHubRepository;
    private final ClusterRepository clusterRepository;
    private final HubSyncRunRepository syncRunRepository;
    private final SnapshotIngestionService ingestionService;
    private final ClusterDiscovery clusterDiscovery;
    private final ObjectProvider<AcmHubClient> hubClientProvider;
    private final HubResilience hubResilience;
    private final AcmProperties properties;
    private final NodeAgentReportService nodeAgentReports;

    /**
     * Periodic scheduled collection across all configured ACM Hubs; the lock keeps each cycle to one replica.
     */
    @Scheduled(cron = "${openshift.portal.collector.cron:0 */15 * * * *}")
    @SchedulerLock(name = "acm-collection", lockAtMostFor = "PT14M", lockAtLeastFor = "PT1M")
    public void scheduledCollection() {
        if (!properties.getCollector().isEnabled()) {
            log.info("Collector is disabled in configuration. Skipping scheduled run.");
            return;
        }
        log.info("Triggering scheduled ACM Hub snapshot collection...");
        triggerCollection();
    }

    /**
     * Collects every registered ACM Hub. Hubs are isolated from each other: a failing hub is recorded and the
     * others carry on.
     */
    public SnapshotTriggerResultDto triggerCollection() {
        long start = System.currentTimeMillis();
        AcmHubClient hubClient = hubClientProvider.getIfAvailable();
        if (hubClient == null) {
            String message = "No ACM client is configured; enable the simulator or configure a live ACM client.";
            log.warn(message);
            return SnapshotTriggerResultDto.builder().status("SKIPPED").message(message).build();
        }

        int clustersProcessed = 0;
        int snapshotsCreated = 0;
        List<String> hubsNeedingAttention = new ArrayList<>();
        for (AcmHub hub : acmHubRepository.findAll(Sort.by("name"))) {
            HubSyncRun run = syncHub(hub, hubClient);
            clustersProcessed += run.getClustersOk() + run.getClustersFailed();
            snapshotsCreated += run.getClustersOk();
            if (run.getStatus() != SyncStatus.SUCCESS) {
                hubsNeedingAttention.add(hub.getName() + " (" + run.getStatus() + ")");
            }
        }

        long duration = System.currentTimeMillis() - start;
        String message = String.format("Processed %d clusters and captured %d snapshots in %d ms.",
                clustersProcessed, snapshotsCreated, duration);
        if (!hubsNeedingAttention.isEmpty()) {
            message += " Hubs needing attention: " + String.join(", ", hubsNeedingAttention) + ".";
        }
        return SnapshotTriggerResultDto.builder()
                .clustersProcessed(clustersProcessed)
                .snapshotsCreated(snapshotsCreated)
                .durationMs(duration)
                .status(hubsNeedingAttention.isEmpty() ? "COMPLETED" : "PARTIAL")
                .message(message)
                .build();
    }

    /**
     * Syncs one hub: the remote read runs under the hub's retry and circuit breaker outside any transaction, each
     * cluster is then stored in its own short transaction, and the outcome is recorded as a sync run.
     */
    private HubSyncRun syncHub(AcmHub hub, AcmHubClient hubClient) {
        HubSyncRun run = HubSyncRun.builder().hub(hub).startedAt(LocalDateTime.now()).build();
        AtomicInteger attempts = new AtomicInteger();
        HubStatus hubStatus;
        try {
            List<ClusterObservation> observations = hubResilience.call(hub, () -> {
                attempts.incrementAndGet();
                return hubClient.fetchClusters(hub);
            });
            ingestObservations(hub, observations, run);
            hubStatus = run.getStatus() == SyncStatus.SUCCESS ? HubStatus.ACTIVE : HubStatus.DEGRADED;
        } catch (Exception e) {
            boolean breakerOpen = e instanceof CallNotPermittedException;
            run.setStatus(breakerOpen && attempts.get() == 0 ? SyncStatus.SKIPPED_CIRCUIT_OPEN : SyncStatus.FAILED);
            run.setErrorMessage(describe(e));
            hubStatus = breakerOpen || e instanceof AcmConnectionException ? HubStatus.UNREACHABLE : HubStatus.ERROR;
            log.warn("ACM Hub {} sync {} after {} attempt(s): {}", hub.getName(), run.getStatus(), attempts.get(), describe(e));
        }
        run.setAttempts(attempts.get());
        run.setFinishedAt(LocalDateTime.now());

        if (run.getClustersOk() > 0 || run.getStatus() == SyncStatus.SUCCESS) {
            hub.setLastSyncTimestamp(run.getFinishedAt());
            hub.setConsecutiveFailures(0);
        } else {
            hub.setConsecutiveFailures(hub.getConsecutiveFailures() + 1);
        }
        hub.setStatus(hubStatus);
        acmHubRepository.save(hub);
        return syncRunRepository.save(run);
    }

    private void ingestObservations(AcmHub hub, List<ClusterObservation> observations, HubSyncRun run) {
        Map<String, Cluster> clustersByName = clusterRepository.findByAcmHubId(hub.getId()).stream()
                .collect(Collectors.toMap(Cluster::getClusterName, Function.identity()));
        LocalDateTime timestamp = LocalDateTime.now();
        int ok = 0;
        int failed = 0;

        for (ClusterObservation observation : observations) {
            Cluster cluster = clustersByName.get(observation.clusterName());
            if (cluster == null && observation.metadata() != null) {
                cluster = clusterRepository.save(clusterDiscovery.newCluster(hub, observation.clusterName(), observation.metadata()));
                log.info("Registered cluster {} discovered on ACM Hub {}", cluster.getClusterName(), hub.getName());
            } else if (cluster != null && observation.metadata() != null
                    && clusterDiscovery.refresh(cluster, observation.metadata())) {
                cluster = clusterRepository.save(cluster);
            }
            if (cluster == null) {
                log.warn("ACM Hub {} reported unknown cluster {} without metadata; skipping it", hub.getName(),
                        observation.clusterName());
                continue;
            }
            if (observation.isFailed()) {
                failed++;
                log.warn("Could not read cluster {} from ACM Hub {}: {}", observation.clusterName(), hub.getName(),
                        observation.error());
                continue;
            }
            try {
                ingestionService.ingest(cluster, withAgentNodes(observation), timestamp, true);
                ok++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to store the snapshot of cluster {} from ACM Hub {}", observation.clusterName(),
                        hub.getName(), e);
            }
        }

        run.setClustersOk(ok);
        run.setClustersFailed(failed);
        run.setStatus(failed == 0 ? SyncStatus.SUCCESS : ok > 0 ? SyncStatus.PARTIAL : SyncStatus.FAILED);
        if (failed > 0) {
            run.setErrorMessage(failed + " of " + (ok + failed) + " clusters could not be collected");
        }
    }

    /**
     * Hubs describe clusters but not their nodes, so a live observation gets its nodes from the cluster's node agent
     * while that agent's latest report is fresh. Observations that already carry nodes are kept as they are.
     */
    private ClusterObservation withAgentNodes(ClusterObservation observation) {
        if (!observation.nodes().isEmpty()) {
            return observation;
        }
        return nodeAgentReports.freshNodes(observation.clusterName()).map(observation::withNodes).orElse(observation);
    }

    private static String describe(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
