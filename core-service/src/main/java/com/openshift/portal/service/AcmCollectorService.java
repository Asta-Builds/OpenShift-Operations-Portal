package com.openshift.portal.service;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.exception.AcmConnectionException;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcmCollectorService {

    private final AcmHubRepository acmHubRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeMetricsRepository;
    private final LicensingService licensingService;
    private final AcmSimulatorService simulatorService;
    private final AcmProperties properties;

    @PostConstruct
    public void init() {
        if (properties.getSimulator().isEnabled()) {
            simulatorService.seedInitialFleetIfEmpty();
        }
    }

    /**
     * Periodic scheduled collection across all configured ACM Hubs.
     */
    @Scheduled(cron = "${openshift.portal.collector.cron:0 */15 * * * *}")
    public void scheduledCollection() {
        if (!properties.getCollector().isEnabled()) {
            log.info("Collector is disabled in configuration. Skipping scheduled run.");
            return;
        }
        log.info("Triggering scheduled ACM Hub snapshot collection...");
        triggerCollection();
    }

    /**
     * Executes snapshot collection across all registered ACM Hubs with Resilience4j fault protection.
     */
    public SnapshotTriggerResultDto triggerCollection() {
        long start = System.currentTimeMillis();
        List<AcmHub> hubs = acmHubRepository.findAll();
        int totalClustersProcessed = 0;
        int totalSnapshotsCreated = 0;

        for (AcmHub hub : hubs) {
            try {
                int created = collectFromHubWithResilience(hub);
                totalSnapshotsCreated += created;
                totalClustersProcessed += created; // one snapshot per collected cluster
            } catch (Exception e) {
                log.error("Failed to collect snapshots for ACM Hub {}: {}", hub.getName(), e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        return SnapshotTriggerResultDto.builder()
                .clustersProcessed(totalClustersProcessed)
                .snapshotsCreated(totalSnapshotsCreated)
                .durationMs(duration)
                .status("COMPLETED")
                .message(String.format("Successfully processed %d clusters and captured %d snapshots in %d ms.",
                        totalClustersProcessed, totalSnapshotsCreated, duration))
                .build();
    }

    @CircuitBreaker(name = "acmHubService", fallbackMethod = "collectionFallback")
    @Retry(name = "acmHubService")
    @Transactional
    public int collectFromHubWithResilience(AcmHub hub) {
        log.info("Polling ACM Hub: {} ({})", hub.getName(), hub.getApiUrl());

        // In simulator / offline mode or when live ACM is simulated
        if (properties.getSimulator().isEnabled()) {
            simulatorService.fetchFromHub(hub);
        }

        LocalDateTime now = LocalDateTime.now();
        int createdCount = 0;

        for (Cluster cluster : clusterRepository.findByAcmHubId(hub.getId())) {
            // No live ACM client exists yet (plan Phase 3), so the node inventory comes from the simulated topology
            AcmSimulatorService.SimulatedTopology topology = simulatorService.simulateTopology(cluster, now, 0);
            ClusterSnapshot snapshot = snapshotRepository.save(createSnapshotForCluster(cluster, now, topology));
            for (NodeMetricsSnapshot node : topology.nodes()) {
                node.setSnapshot(snapshot);
            }
            nodeMetricsRepository.saveAll(topology.nodes());
            createdCount++;
        }

        hub.setStatus(HubStatus.ACTIVE);
        hub.setLastSyncTimestamp(now);
        acmHubRepository.save(hub);

        return createdCount;
    }

    /**
     * Fallback method invoked when Circuit Breaker is open or retries are exhausted.
     */
    public int collectionFallback(AcmHub hub, Throwable t) {
        log.warn("Resilience fallback triggered for ACM Hub {}: {}", hub.getName(), t.getMessage());
        hub.setStatus(HubStatus.UNREACHABLE);
        acmHubRepository.save(hub);
        return 0;
    }

    private ClusterSnapshot createSnapshotForCluster(Cluster cluster, LocalDateTime timestamp,
                                                     AcmSimulatorService.SimulatedTopology topology) {
        int totalCores = topology.totalCpuCores();
        int allocatedCores = (int) Math.round(totalCores * (0.60 + Math.random() * 0.25));
        BigDecimal totalMem = topology.totalMemoryGb();
        BigDecimal allocatedMem = totalMem.multiply(BigDecimal.valueOf(0.55 + Math.random() * 0.25));

        int licenseCores = licensingService.calculateLicenseCores(topology.nodes(), cluster.getInfrastructureType());

        BigDecimal totalStorage = BigDecimal.valueOf(totalCores * 40.0);
        BigDecimal allocatedStorage = totalStorage.multiply(BigDecimal.valueOf(0.55 + Math.random() * 0.25));

        return ClusterSnapshot.builder()
                .cluster(cluster)
                .snapshotTimestamp(timestamp)
                .totalCpuCores(totalCores)
                .allocatedCpuCores(allocatedCores)
                .totalMemoryGb(totalMem)
                .allocatedMemoryGb(allocatedMem)
                .totalStorageGb(totalStorage)
                .allocatedStorageGb(allocatedStorage)
                .licenseCoresCount(licenseCores)
                .totalNodes(topology.nodes().size())
                .workerNodes(topology.workerNodes())
                .rawPayload(String.format("{\"cluster\": \"%s\", \"collectedAt\": \"%s\"}",
                        cluster.getClusterName(), timestamp))
                .build();
    }
}
