package com.openshift.portal.service;

import com.openshift.portal.domain.entity.*;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.exception.AcmConnectionException;
import com.openshift.portal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcmSimulatorService {

    private final AcmHubRepository acmHubRepository;
    private final TeamRepository teamRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeMetricsRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceSnapshotRepository namespaceSnapshotRepository;
    private final LicenseWatermarkRepository watermarkRepository;
    private final LicensingService licensingService;
    private final ProviderIdParserService providerIdParser;

    private boolean failNextCall = false;

    public void setSimulateFailure(boolean fail) {
        this.failNextCall = fail;
        log.warn("Simulator failure injection set to: {}", fail);
    }

    public boolean isSimulateFailure() {
        return this.failNextCall;
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

            // Generate 3 history points: 30 days ago, 15 days ago, and today
            for (int daysAgo : List.of(30, 15, 0)) {
                LocalDateTime snapshotTime = now.minusDays(daysAgo);
                SimulatedTopology topology = simulateTopology(cluster, snapshotTime, daysAgo);
                List<NodeMetricsSnapshot> nodes = topology.nodes();

                int totalCores = topology.totalCpuCores();
                int allocatedCores = (int) Math.round(totalCores * (0.65 + (30 - daysAgo) * 0.005));
                BigDecimal totalMem = topology.totalMemoryGb();
                BigDecimal allocatedMem = totalMem.multiply(BigDecimal.valueOf(0.60 + (30 - daysAgo) * 0.004));

                BigDecimal totalStorage = BigDecimal.valueOf(totalCores * 40.0);
                BigDecimal allocatedStorage = totalStorage.multiply(BigDecimal.valueOf(0.55 + (30 - daysAgo) * 0.003));

                int licenseCores = licensingService.calculateLicenseCores(nodes, cluster.getInfrastructureType());
                if (daysAgo == 0) {
                    totalPeakWorkerCores += licenseCores;
                }

                ClusterSnapshot snapshot = ClusterSnapshot.builder()
                        .cluster(cluster)
                        .snapshotTimestamp(snapshotTime)
                        .totalCpuCores(totalCores)
                        .allocatedCpuCores(allocatedCores)
                        .totalMemoryGb(totalMem)
                        .allocatedMemoryGb(allocatedMem)
                        .totalStorageGb(totalStorage)
                        .allocatedStorageGb(allocatedStorage)
                        .licenseCoresCount(licenseCores)
                        .totalNodes(nodes.size())
                        .workerNodes(topology.workerNodes())
                        .rawPayload(String.format("{\"cluster\": \"%s\", \"simulated\": true, \"timestamp\": \"%s\"}",
                                cluster.getClusterName(), snapshotTime))
                        .build();

                ClusterSnapshot savedSnapshot = snapshotRepository.save(snapshot);

                // Set backreference and save node metrics for current snapshot
                if (daysAgo == 0) {
                    for (NodeMetricsSnapshot node : nodes) {
                        node.setSnapshot(savedSnapshot);
                    }
                    nodeMetricsRepository.saveAll(nodes);
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
     * Simulated node inventory of a cluster {@code daysAgo} days before the end of the seeded history. The worker
     * count grows along that history, so live collections (daysAgo = 0) keep the latest seeded topology.
     */
    public SimulatedTopology simulateTopology(Cluster cluster, LocalDateTime timestamp, int daysAgo) {
        boolean bareMetal = cluster.getInfrastructureType() == InfrastructureType.BARE_METAL;
        int workerCount = (bareMetal ? 8 : 6) + (30 - daysAgo) / 10;
        int coresPerWorker = bareMetal ? 32 : 16;
        int memGbPerWorker = bareMetal ? 128 : 64;

        List<NodeMetricsSnapshot> nodes = new ArrayList<>();
        // Master nodes
        for (int m = 1; m <= 3; m++) {
            String providerId = generateProviderId(cluster.getInfrastructureType(), "master", m);
            var providerInfo = providerIdParser.parseProviderId(providerId);

            nodes.add(NodeMetricsSnapshot.builder()
                    .cluster(cluster)
                    .snapshotTimestamp(timestamp)
                    .nodeName(String.format("%s-master-%d", cluster.getClusterName(), m))
                    .role(NodeRole.MASTER)
                    .hostType(cluster.getInfrastructureType().name())
                    .cpuCores(8)
                    .memoryGb(BigDecimal.valueOf(32))
                    .underlyingHostId(providerInfo.getInstanceId())
                    .providerId(providerId)
                    .hypervisorHost(providerInfo.getHypervisorHost())
                    .sockets(2)
                    .build());
        }
        // Worker nodes
        for (int w = 1; w <= workerCount; w++) {
            String providerId = generateProviderId(cluster.getInfrastructureType(), "worker", w);
            var providerInfo = providerIdParser.parseProviderId(providerId);

            nodes.add(NodeMetricsSnapshot.builder()
                    .cluster(cluster)
                    .snapshotTimestamp(timestamp)
                    .nodeName(String.format("%s-worker-%02d", cluster.getClusterName(), w))
                    .role(NodeRole.WORKER)
                    .hostType(cluster.getInfrastructureType().name())
                    .cpuCores(coresPerWorker)
                    .memoryGb(BigDecimal.valueOf(memGbPerWorker))
                    .underlyingHostId(providerInfo.getInstanceId())
                    .providerId(providerId)
                    .hypervisorHost(providerInfo.getHypervisorHost())
                    .sockets(bareMetal ? 2 : 1)
                    .build());
        }

        int totalCores = (workerCount * coresPerWorker) + (3 * 8);
        BigDecimal totalMem = BigDecimal.valueOf((workerCount * memGbPerWorker) + (3 * 32));
        return new SimulatedTopology(nodes, workerCount, totalCores, totalMem);
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

    /**
     * Poll simulated ACM Hub for updated metrics (checks for failure injection).
     */
    public List<SimulatedClusterPayload> fetchFromHub(AcmHub hub) {
        if (failNextCall) {
            failNextCall = false; // Reset after triggering
            throw new AcmConnectionException("Simulated connection timeout to ACM Hub: " + hub.getApiUrl());
        }

        List<SimulatedClusterPayload> payloads = new ArrayList<>();
        List<Cluster> clusters = clusterRepository.findAll();
        for (Cluster c : clusters) {
            if (c.getAcmHub() != null && c.getAcmHub().getId().equals(hub.getId())) {
                payloads.add(new SimulatedClusterPayload(c.getClusterName(), c.getInfrastructureType()));
            }
        }
        return payloads;
    }

    public record SimulatedClusterPayload(String clusterName, InfrastructureType infrastructureType) {}

    public record SimulatedTopology(List<NodeMetricsSnapshot> nodes, int workerNodes, int totalCpuCores,
                                    BigDecimal totalMemoryGb) {}
}
