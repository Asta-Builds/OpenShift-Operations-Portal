package com.openshift.portal.service;

import com.openshift.portal.acm.ClusterObservation;
import com.openshift.portal.acm.NodeObservation;
import com.openshift.portal.domain.entity.*;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.exception.AcmConnectionException;
import com.openshift.portal.exception.ResourceNotFoundException;
import com.openshift.portal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcmSimulatorService {

    /** Days of seeded history; on the growth curve, day 0 is its first day and this value is the seeding day. */
    private static final int SEED_HISTORY_DAYS = 30;

    private final AcmHubRepository acmHubRepository;
    private final TeamRepository teamRepository;
    private final ClusterRepository clusterRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceSnapshotRepository namespaceSnapshotRepository;
    private final LicenseWatermarkRepository watermarkRepository;
    private final SnapshotIngestionService ingestionService;

    private volatile boolean failNextCall = false;
    private final Set<String> hubOutages = ConcurrentHashMap.newKeySet();
    private final Set<String> failingClusters = ConcurrentHashMap.newKeySet();

    /** One-shot fault: the next hub call fails once, whichever hub it targets. */
    public void setSimulateFailure(boolean fail) {
        this.failNextCall = fail;
        log.warn("Simulator failure injection set to: {}", fail);
    }

    public boolean isSimulateFailure() {
        return this.failNextCall;
    }

    /** Persistent outage: every call to the hub fails until the outage is cleared. */
    public void setHubOutage(String hubName, boolean down) {
        acmHubRepository.findByName(hubName)
                .orElseThrow(() -> new ResourceNotFoundException("ACM hub not found: " + hubName));
        if (down) {
            hubOutages.add(hubName);
        } else {
            hubOutages.remove(hubName);
        }
        log.warn("Simulated outage of ACM hub {} set to: {}", hubName, down);
    }

    /** Persistent cluster failure: the hub answers, but this cluster's data cannot be read. */
    public void setClusterFailure(String clusterName, boolean failing) {
        clusterRepository.findByClusterName(clusterName)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found: " + clusterName));
        if (failing) {
            failingClusters.add(clusterName);
        } else {
            failingClusters.remove(clusterName);
        }
        log.warn("Simulated failure of cluster {} set to: {}", clusterName, failing);
    }

    public List<String> getHubOutages() {
        return hubOutages.stream().sorted().toList();
    }

    public List<String> getFailingClusters() {
        return failingClusters.stream().sorted().toList();
    }

    public void clearFaults() {
        failNextCall = false;
        hubOutages.clear();
        failingClusters.clear();
    }

    /**
     * Seeds initial enterprise clusters, ACM hubs, namespaces, and historical snapshots for local sandbox testing.
     *
     * @return true if the fleet was seeded, false if clusters already existed and nothing changed
     */
    @Transactional
    public boolean seedInitialFleetIfEmpty() {
        if (clusterRepository.count() > 0) {
            log.info("Fleet already seeded ({} clusters present).", clusterRepository.count());
            return false;
        }

        log.info("Seeding realistic enterprise OpenShift fleet into database...");

        // 1. Teams
        Team paymentsTeam = teamRepository.save(Team.builder()
                .name("Payments Platform")
                .costCenter("CC-FIN-104")
                .contactEmail("payments-infra@enterprise.internal")
                .build());

        Team digitalTeam = teamRepository.save(Team.builder()
                .name("Digital Channels")
                .costCenter("CC-DIG-205")
                .contactEmail("digital-devops@enterprise.internal")
                .build());

        Team dataScienceTeam = teamRepository.save(Team.builder()
                .name("Data & AI Analytics")
                .costCenter("CC-AI-900")
                .contactEmail("data-platform@enterprise.internal")
                .build());

        // 2. ACM Hubs
        AcmHub primaryHub = acmHubRepository.save(AcmHub.builder()
                .name("acm-hub-primary-eu")
                .apiUrl("https://api.acm-hub-primary.internal:6443")
                .authToken("mock-bearer-token-primary")
                .status(HubStatus.ACTIVE)
                .build());

        AcmHub secondaryHub = acmHubRepository.save(AcmHub.builder()
                .name("acm-hub-secondary-us")
                .apiUrl("https://api.acm-hub-secondary.internal:6443")
                .authToken("mock-bearer-token-secondary")
                .status(HubStatus.ACTIVE)
                .build());

        // 3. Realistic Clusters
        List<Cluster> clusterDefs = List.of(
                Cluster.builder()
                        .acmHub(primaryHub)
                        .clusterName("ocp-prod-eu-west-01")
                        .environment(Environment.PRODUCTION)
                        .ownerTeam(paymentsTeam)
                        .infrastructureType(InfrastructureType.BARE_METAL)
                        .openshiftVersion("4.14.28")
                        .region("eu-west-1")
                        .status("READY")
                        .build(),
                Cluster.builder()
                        .acmHub(primaryHub)
                        .clusterName("ocp-prod-eu-central-02")
                        .environment(Environment.PRODUCTION)
                        .ownerTeam(digitalTeam)
                        .infrastructureType(InfrastructureType.VMWARE)
                        .openshiftVersion("4.14.30")
                        .region("eu-central-1")
                        .status("READY")
                        .build(),
                Cluster.builder()
                        .acmHub(primaryHub)
                        .clusterName("ocp-ai-training-prod")
                        .environment(Environment.PRODUCTION)
                        .ownerTeam(dataScienceTeam)
                        .infrastructureType(InfrastructureType.BARE_METAL)
                        .openshiftVersion("4.15.12")
                        .region("eu-west-1")
                        .status("READY")
                        .build(),
                Cluster.builder()
                        .acmHub(secondaryHub)
                        .clusterName("ocp-staging-us-east-01")
                        .environment(Environment.STAGING)
                        .ownerTeam(paymentsTeam)
                        .infrastructureType(InfrastructureType.AWS)
                        .openshiftVersion("4.14.28")
                        .region("us-east-1")
                        .status("READY")
                        .build(),
                Cluster.builder()
                        .acmHub(secondaryHub)
                        .clusterName("ocp-dev-us-east-sandbox")
                        .environment(Environment.DEVELOPMENT)
                        .ownerTeam(digitalTeam)
                        .infrastructureType(InfrastructureType.AWS)
                        .openshiftVersion("4.15.10")
                        .region("us-east-1")
                        .status("READY")
                        .build()
        );

        List<Cluster> savedClusters = clusterRepository.saveAll(clusterDefs);

        // 4. Generate historical snapshots (30 days back + today)
        LocalDateTime now = LocalDateTime.now();
        int totalPeakWorkerCores = 0;

        for (Cluster cluster : savedClusters) {
            // Seed Namespaces for owner-aware namespace level attribution
            seedNamespacesForCluster(cluster);

            // Generate 3 history points: 30 days ago, 15 days ago, and today (only today's keeps its node rows)
            for (int daysAgo : List.of(30, 15, 0)) {
                LocalDateTime snapshotTime = now.minusDays(daysAgo);
                ClusterObservation observation = simulateObservation(cluster, snapshotTime, SEED_HISTORY_DAYS - daysAgo);
                ClusterSnapshot snapshot = ingestionService.ingest(cluster, observation, snapshotTime, daysAgo == 0);
                if (daysAgo == 0) {
                    totalPeakWorkerCores += snapshot.getLicenseCoresCount();
                }
            }
        }

        // 5. Seed License Watermark Audit record
        watermarkRepository.save(LicenseWatermark.builder()
                .watermarkDate(LocalDate.now())
                .peakWorkerCores(totalPeakWorkerCores)
                .licensedCapCores(500)
                .complianceBreach(totalPeakWorkerCores > 500)
                .build());

        log.info("Fleet seeding complete with {} clusters and historical snapshots.", savedClusters.size());
        return true;
    }

    /**
     * Simulated answer of an ACM hub: the current state of each of its clusters, continuing the seeded growth curve.
     *
     * @throws AcmConnectionException for an injected one-shot fault or hub outage
     */
    public List<ClusterObservation> observeHub(AcmHub hub, LocalDateTime timestamp) {
        if (failNextCall) {
            failNextCall = false; // Reset after triggering
            throw new AcmConnectionException("Simulated connection timeout to ACM Hub: " + hub.getApiUrl());
        }
        if (hubOutages.contains(hub.getName())) {
            throw new AcmConnectionException("Simulated outage of ACM Hub: " + hub.getApiUrl());
        }

        List<ClusterObservation> observations = new ArrayList<>();
        for (Cluster cluster : clusterRepository.findByAcmHubId(hub.getId())) {
            if (failingClusters.contains(cluster.getClusterName())) {
                observations.add(ClusterObservation.failed(cluster.getClusterName(),
                        "Simulated metrics query timeout for cluster " + cluster.getClusterName()));
                continue;
            }
            // Clusters are created on the seeding day, so their age extends the seeded history
            long daysSinceSeeding = cluster.getCreatedAt() != null
                    ? ChronoUnit.DAYS.between(cluster.getCreatedAt(), timestamp) : 0;
            observations.add(simulateObservation(cluster, timestamp, SEED_HISTORY_DAYS + (int) daysSinceSeeding));
        }
        return observations;
    }

    /**
     * Simulated state of a cluster {@code growthDays} days into its growth curve: workers, allocation and storage
     * all grow with it, so seeded history and later collections form one consistent series.
     */
    private ClusterObservation simulateObservation(Cluster cluster, LocalDateTime timestamp, int growthDays) {
        boolean bareMetal = cluster.getInfrastructureType() == InfrastructureType.BARE_METAL;
        int workerCount = (bareMetal ? 8 : 6) + growthDays / 10;
        int coresPerWorker = bareMetal ? 32 : 16;
        int memGbPerWorker = bareMetal ? 128 : 64;

        List<NodeObservation> nodes = new ArrayList<>();
        // Master nodes
        for (int m = 1; m <= 3; m++) {
            nodes.add(new NodeObservation(String.format("%s-master-%d", cluster.getClusterName(), m), NodeRole.MASTER,
                    8, BigDecimal.valueOf(32), generateProviderId(cluster.getInfrastructureType(), "master", m), 2));
        }
        // Worker nodes
        for (int w = 1; w <= workerCount; w++) {
            nodes.add(new NodeObservation(String.format("%s-worker-%02d", cluster.getClusterName(), w), NodeRole.WORKER,
                    coresPerWorker, BigDecimal.valueOf(memGbPerWorker),
                    generateProviderId(cluster.getInfrastructureType(), "worker", w), bareMetal ? 2 : 1));
        }

        int totalCores = (workerCount * coresPerWorker) + (3 * 8);
        BigDecimal totalMem = BigDecimal.valueOf((workerCount * memGbPerWorker) + (3 * 32));
        BigDecimal totalStorage = BigDecimal.valueOf(totalCores * 40.0);

        return new ClusterObservation(
                cluster.getClusterName(),
                totalCores,
                (int) Math.round(totalCores * growthFraction(0.65, 0.005, growthDays)),
                totalMem,
                scaleBy(totalMem, growthFraction(0.60, 0.004, growthDays)),
                totalStorage,
                scaleBy(totalStorage, growthFraction(0.55, 0.003, growthDays)),
                nodes,
                String.format("{\"cluster\": \"%s\", \"simulated\": true, \"timestamp\": \"%s\"}",
                        cluster.getClusterName(), timestamp),
                null);
    }

    /** Share of capacity in use after {@code growthDays}, capped below full. */
    private static double growthFraction(double start, double dailyIncrease, int growthDays) {
        return Math.min(0.95, start + (growthDays * dailyIncrease));
    }

    private static BigDecimal scaleBy(BigDecimal value, double fraction) {
        return value.multiply(BigDecimal.valueOf(fraction)).setScale(2, RoundingMode.HALF_UP);
    }

    private void seedNamespacesForCluster(Cluster cluster) {
        List<String> nsNames = List.of("frontend-ui", "api-gateway", "data-pipeline", "payments-engine");
        for (String name : nsNames) {
            Namespace ns = namespaceRepository.save(Namespace.builder()
                    .cluster(cluster)
                    .namespaceName(name)
                    .ownerTeam(cluster.getOwnerTeam())
                    .costCenter(cluster.getOwnerTeam() != null ? cluster.getOwnerTeam().getCostCenter() : "CC-GEN-001")
                    .build());

            namespaceSnapshotRepository.save(NamespaceSnapshot.builder()
                    .namespace(ns)
                    .snapshotTimestamp(LocalDateTime.now())
                    .cpuRequestCores(BigDecimal.valueOf(8.00))
                    .cpuLimitCores(BigDecimal.valueOf(16.00))
                    .memoryRequestGb(BigDecimal.valueOf(32.00))
                    .memoryLimitGb(BigDecimal.valueOf(64.00))
                    .build());
        }
    }

    private String generateProviderId(InfrastructureType type, String role, int index) {
        return switch (type) {
            case VMWARE -> String.format("vsphere://421a7192-%s-%04d-4f33-1996d9eb74ca", role, index);
            case AWS -> String.format("aws:///us-east-1a/i-%s%08d", role, index);
            case BARE_METAL -> String.format("baremetal://d407ad32-%s-%04d-bb173f4e2468", role, index);
            case AZURE -> String.format("azure:///subscriptions/sub-01/resourceGroups/rg-ocp/providers/Microsoft.Compute/virtualMachines/%s-%02d", role, index);
            default -> String.format("custom://host-%s-%02d", role, index);
        };
    }

}
