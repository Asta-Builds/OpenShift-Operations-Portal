package com.openshift.portal.service;

import com.openshift.portal.acm.ClusterObservation;
import com.openshift.portal.acm.NamespaceInventory;
import com.openshift.portal.acm.NamespaceObservation;
import com.openshift.portal.acm.NodeObservation;
import com.openshift.portal.config.AcmProperties;
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
    private final TeamAliasRepository teamAliasRepository;
    private final LicenseWatermarkRepository watermarkRepository;
    private final SnapshotIngestionService ingestionService;
    private final AcmProperties properties;
    private final InfrastructureInventoryRepository inventoryRepository;
    private final ProviderIdParserService providerIdParser;

    /**
     * Namespaces every simulated cluster runs, with their share of the cluster's requests. Owner values cover each
     * matching rule: a team name slug, an alias, a team that does not exist, and no owner label at all.
     */
    private record SimulatedNamespace(String name, String owner, String costCenter, int weight, double cpuRatio, double memRatio, int removedAfterDays) {
        SimulatedNamespace(String name, String owner, String costCenter, int weight, double cpuRatio, double memRatio) {
            this(name, owner, costCenter, weight, cpuRatio, memRatio, Integer.MAX_VALUE);
        }
    }

    /**
     * Realistic enterprise workloads per cluster profile:
     * - Banking/Payments core services (well tuned & batch workers)
     * - AI & Data pipelines (LLM inference, vector DB, Feast feature store)
     * - Digital channels (API gateways, web portal, Redis caches)
     * - Staging & integration testbeds
     * - Abandoned developer sandboxes with severe waste
     * - Under-provisioned services operating near saturation (alert triggers)
     */
    private static List<SimulatedNamespace> getClusterNamespaces(Cluster cluster) {
        String name = cluster.getClusterName();
        if (name.contains("prod-eu-west-01")) {
            return List.of(
                    new SimulatedNamespace("payments-core-gateway", "payments-platform", "CC-FIN-104", 6, 0.74, 0.78),
                    new SimulatedNamespace("card-authorization-svc", "payments-platform", "CC-FIN-104", 5, 0.88, 0.86),
                    new SimulatedNamespace("fraud-detection-streaming", "payments-platform", "CC-FIN-104", 4, 1.09, 1.05),
                    new SimulatedNamespace("settlement-batch-worker", "payments-platform", "CC-FIN-104", 5, 0.16, 0.24),
                    new SimulatedNamespace("compliance-audit-vault", "core-banking", "CC-FIN-001", 3, 0.31, 0.38),
                    new SimulatedNamespace("openshift-monitoring", null, null, 2, 0.75, 0.80),
                    new SimulatedNamespace("migration-legacy", "payments-platform", null, 1, 0.12, 0.20, 20)
            );
        } else if (name.contains("prod-eu-central-02")) {
            return List.of(
                    new SimulatedNamespace("customer-web-portal", "digital-channels", "CC-DIG-205", 5, 0.75, 0.78),
                    new SimulatedNamespace("mobile-api-gateway", "digital-channels", "CC-DIG-205", 6, 0.71, 0.74),
                    new SimulatedNamespace("notification-dispatcher", "digital-channels", "CC-DIG-205", 4, 0.22, 0.28),
                    new SimulatedNamespace("redis-distributed-cache", "digital-channels", "CC-DIG-205", 3, 0.86, 0.90),
                    new SimulatedNamespace("graphql-federation-mesh", "digital-channels", "CC-DIG-205", 4, 0.52, 0.58),
                    new SimulatedNamespace("ingress-nginx-edge", null, null, 3, 0.80, 0.78)
            );
        } else if (name.contains("ai-training-prod")) {
            return List.of(
                    new SimulatedNamespace("llm-vllm-inference", "data-science", "CC-AI-900", 7, 0.91, 0.89),
                    new SimulatedNamespace("feature-store-feast", "data-science", "CC-AI-900", 4, 0.48, 0.54),
                    new SimulatedNamespace("spark-batch-analytics", "data-science", "CC-AI-900", 6, 0.23, 0.31),
                    new SimulatedNamespace("vector-database-milvus", "data-science", "CC-AI-900", 4, 1.08, 1.04),
                    new SimulatedNamespace("jupyter-notebook-hub", "data-science", "CC-AI-900", 5, 0.14, 0.22),
                    new SimulatedNamespace("gpu-operator-system", null, null, 2, 0.85, 0.82)
            );
        } else if (name.contains("staging")) {
            return List.of(
                    new SimulatedNamespace("staging-payments-api", "payments-platform", "CC-FIN-104", 4, 0.19, 0.26),
                    new SimulatedNamespace("staging-digital-hub", "digital-channels", "CC-DIG-205", 4, 0.24, 0.30),
                    new SimulatedNamespace("integration-test-suite", "digital-channels", "CC-DIG-205", 5, 0.42, 0.52),
                    new SimulatedNamespace("mock-partner-gateways", "payments-platform", "CC-FIN-104", 3, 0.15, 0.22),
                    new SimulatedNamespace("staging-monitoring", null, null, 2, 0.65, 0.70)
            );
        } else {
            // Development cluster (dev sandbox with idle pods)
            return List.of(
                    new SimulatedNamespace("dev-sandbox-alice", "digital-channels", "CC-DIG-205", 4, 0.08, 0.15),
                    new SimulatedNamespace("dev-sandbox-bob", "payments-platform", "CC-FIN-104", 4, 0.06, 0.12),
                    new SimulatedNamespace("qa-automated-e2e", "payments-platform", "CC-FIN-104", 4, 0.36, 0.44),
                    new SimulatedNamespace("ci-cd-ephemeral-runners", "digital-channels", "CC-DIG-205", 5, 0.58, 0.62),
                    new SimulatedNamespace("zombie-feature-branch", null, null, 3, 0.02, 0.08),
                    new SimulatedNamespace("sandbox-tmp", null, null, 2, 0.10, 0.18, 20)
            );
        }
    }

    /** Owner values the simulated namespaces use that differ from a team name. */
    private static final Map<String, String> SIMULATED_ALIASES = Map.of("data-science", "Data & AI Analytics");

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

        ensureSimulatorAliases();

        // 2. ACM Hubs
        AcmHub primaryHub = acmHubRepository.save(AcmHub.builder()
                .name("acm-hub-primary-eu")
                .apiUrl("https://api.acm-hub-primary.internal:6443")
                .credentialsSecretRef("acm-hub-primary-eu-credentials")
                .status(HubStatus.ACTIVE)
                .build());

        AcmHub secondaryHub = acmHubRepository.save(AcmHub.builder()
                .name("acm-hub-secondary-us")
                .apiUrl("https://api.acm-hub-secondary.internal:6443")
                .credentialsSecretRef("acm-hub-secondary-us-credentials")
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
        int cap = properties.getLicensing().getLicensedCapCores();
        watermarkRepository.save(LicenseWatermark.builder()
                .watermarkDate(LocalDate.now())
                .peakWorkerCores(totalPeakWorkerCores)
                .licensedCapCores(cap)
                .complianceBreach(totalPeakWorkerCores > cap)
                .build());

        log.info("Fleet seeding complete with {} clusters and historical snapshots.", savedClusters.size());
        return true;
    }

    /**
     * Adds the aliases the simulated namespaces rely on, for teams that exist and aliases not yet defined. Runs at
     * every start so fleets seeded before aliases existed get them too.
     */
    @Transactional
    public void ensureSimulatorAliases() {
        SIMULATED_ALIASES.forEach((alias, teamName) -> teamRepository.findByName(teamName).ifPresent(team -> {
            String normalized = OwnerResolver.normalize(alias);
            if (teamAliasRepository.findByAlias(normalized).isEmpty()) {
                teamAliasRepository.save(TeamAlias.builder().team(team).alias(normalized).build());
                log.info("Added simulator alias {} for team {}", normalized, teamName);
            }
        }));
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
        int workerCount = baseWorkers(cluster) + growthDays / 10;
        int coresPerWorker = bareMetal ? 32 : 16;
        int memGbPerWorker = bareMetal ? 128 : 64;

        List<NodeObservation> nodes = new ArrayList<>();
        // Master nodes
        for (int m = 1; m <= 3; m++) {
            nodes.add(new NodeObservation(String.format("%s-master-%d", cluster.getClusterName(), m), NodeRole.MASTER,
                    8, BigDecimal.valueOf(32), providerId(cluster, "master", m)));
        }
        // Worker nodes
        for (int w = 1; w <= workerCount; w++) {
            nodes.add(new NodeObservation(String.format("%s-worker-%02d", cluster.getClusterName(), w), NodeRole.WORKER,
                    coresPerWorker, BigDecimal.valueOf(memGbPerWorker),
                    providerId(cluster, "worker", w)));
        }

        int totalCores = (workerCount * coresPerWorker) + (3 * 8);
        BigDecimal totalMem = BigDecimal.valueOf((workerCount * memGbPerWorker) + (3 * 32));
        BigDecimal totalStorage = BigDecimal.valueOf(totalCores * 40.0);

        int allocatedCores = (int) Math.round(totalCores * growthFraction(0.65, 0.005, growthDays));
        BigDecimal allocatedMem = scaleBy(totalMem, growthFraction(0.60, 0.004, growthDays));
        BigDecimal allocatedStorage = scaleBy(totalStorage, growthFraction(0.55, 0.003, growthDays));

        return new ClusterObservation(
                cluster.getClusterName(),
                totalCores,
                BigDecimal.valueOf(allocatedCores),
                totalMem,
                allocatedMem,
                totalStorage,
                allocatedStorage,
                nodes,
                simulateNamespaces(cluster, growthDays, BigDecimal.valueOf(allocatedCores), allocatedMem, allocatedStorage),
                String.format("{\"cluster\": \"%s\", \"simulated\": true, \"timestamp\": \"%s\"}",
                        cluster.getClusterName(), timestamp),
                null,
                null);
    }

    /**
     * Splits the cluster's requests across its realistic namespaces by weight, to the hundredth, so namespace requests always
     * add up exactly to the cluster's. Usage reflects real production, staging or developer idle patterns.
     */
    private NamespaceInventory simulateNamespaces(Cluster cluster, int growthDays, BigDecimal cpuRequests, BigDecimal memoryRequests,
                                                  BigDecimal pvcRequests) {
        List<SimulatedNamespace> present = getClusterNamespaces(cluster).stream()
                .filter(ns -> growthDays < ns.removedAfterDays())
                .toList();
        List<BigDecimal> cpu = splitByWeight(cpuRequests, present);
        List<BigDecimal> memory = splitByWeight(memoryRequests, present);
        List<BigDecimal> storage = splitByWeight(pvcRequests, present);

        String ownerLabel = properties.getAttribution().getOwnerLabel();
        String costCenterLabel = properties.getAttribution().getCostCenterLabel();
        List<NamespaceObservation> namespaces = new ArrayList<>();
        for (int i = 0; i < present.size(); i++) {
            SimulatedNamespace ns = present.get(i);
            Map<String, String> labels = new HashMap<>();
            labels.put("kubernetes.io/metadata.name", ns.name());
            if (ns.owner() != null) {
                labels.put(ownerLabel, ns.owner());
            }
            if (ns.costCenter() != null) {
                labels.put(costCenterLabel, ns.costCenter());
            }
            namespaces.add(new NamespaceObservation(ns.name(), labels, cpu.get(i), memory.get(i),
                    scaleBy(cpu.get(i), ns.cpuRatio()),
                    scaleBy(memory.get(i), ns.memRatio()),
                    storage.get(i)));
        }
        return new NamespaceInventory(namespaces, true);
    }

    /** Shares of {@code total} in proportion to the weights, rounded down to the hundredth, the rest to the last. */
    private static List<BigDecimal> splitByWeight(BigDecimal total, List<SimulatedNamespace> namespaces) {
        long hundredths = total.movePointRight(2).setScale(0, RoundingMode.DOWN).longValueExact();
        int weights = namespaces.stream().mapToInt(SimulatedNamespace::weight).sum();
        List<BigDecimal> shares = new ArrayList<>();
        long assigned = 0;
        for (int i = 0; i < namespaces.size(); i++) {
            long share = i == namespaces.size() - 1
                    ? hundredths - assigned
                    : hundredths * namespaces.get(i).weight() / weights;
            assigned += share;
            shares.add(BigDecimal.valueOf(share, 2));
        }
        return shares;
    }

    /** Share of capacity in use after {@code growthDays}, capped below full. */
    private static double growthFraction(double start, double dailyIncrease, int growthDays) {
        return Math.min(0.95, start + (growthDays * dailyIncrease));
    }

    private static BigDecimal scaleBy(BigDecimal value, double fraction) {
        return value.multiply(BigDecimal.valueOf(fraction)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Workers the cluster had when it was seeded; the growth model adds more over time. */
    private static int baseWorkers(Cluster cluster) {
        return cluster.getInfrastructureType() == InfrastructureType.BARE_METAL ? 8 : 6;
    }

    /**
     * A providerID in the format the platform really uses, stable for a given node so inventory rows can match it.
     * Clusters on other platforms get none, like UPI installs.
     */
    static String providerId(Cluster cluster, String role, int index) {
        String node = String.format("%s-%s-%02d", cluster.getClusterName(), role, index);
        UUID uuid = UUID.nameUUIDFromBytes(node.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String region = cluster.getRegion() != null ? cluster.getRegion() : "us-east-1";
        return switch (cluster.getInfrastructureType()) {
            case VMWARE -> "vsphere://" + uuid;
            case AWS -> String.format("aws:///%s%c/i-0%s", region, (char) ('a' + index % 3),
                    uuid.toString().replace("-", "").substring(0, 16));
            case BARE_METAL -> String.format("baremetalhost:///openshift-machine-api/%s/%s", node, uuid);
            case AZURE -> String.format("azure:///subscriptions/%s/resourceGroups/%s-rg/providers/Microsoft.Compute/virtualMachines/%s",
                    UUID.nameUUIDFromBytes(cluster.getClusterName().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    cluster.getClusterName(), node);
            case GCP -> String.format("gce://%s-project/%s-%c/%s", cluster.getClusterName(), region, (char) ('a' + index % 3), node);
            case OPENSTACK -> "openstack:///" + uuid;
            default -> "";
        };
    }

    /**
     * Loads a simulated asset inventory for the simulated fleet, under its own SIMULATOR source so it is never taken
     * for a real CMDB. It covers the nodes clusters were seeded with; workers added later by the growth model are
     * left out, as happens when a CMDB lags behind.
     */
    @Transactional
    public void ensureSimulatorInventory() {
        if (inventoryRepository.countBySource(InfrastructureInventory.SOURCE_SIMULATOR) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<InfrastructureInventory> rows = new ArrayList<>();
        for (Cluster cluster : clusterRepository.findAll()) {
            boolean bareMetal = cluster.getInfrastructureType() == InfrastructureType.BARE_METAL;
            if (!bareMetal && cluster.getInfrastructureType() != InfrastructureType.VMWARE) {
                continue; // clouds need no inventory
            }
            String datacenter = "dc-" + (cluster.getRegion() != null ? cluster.getRegion() : "main");
            for (String role : List.of("master", "worker")) {
                int count = role.equals("master") ? 3 : baseWorkers(cluster);
                for (int i = 1; i <= count; i++) {
                    var ref = providerIdParser.parse(providerId(cluster, role, i));
                    InfrastructureInventory.InfrastructureInventoryBuilder row = InfrastructureInventory.builder()
                            .source(InfrastructureInventory.SOURCE_SIMULATOR)
                            .providerType(ref.type())
                            .instanceKey(ref.instanceKey())
                            .datacenter(datacenter)
                            .syncedAt(now);
                    if (bareMetal) {
                        // The node is the machine: 8 logical CPUs on masters, 32 on workers
                        row.physicalSockets(role.equals("master") ? 1 : 2)
                                .physicalCores(role.equals("master") ? 4 : 16)
                                .threadsPerCore(2);
                    } else {
                        // VMs spread over a shared vSphere cluster of six dual-socket hosts
                        row.hypervisorCluster("vsan-" + cluster.getRegion())
                                .hypervisorHost(String.format("esx-%02d.%s.corp.internal", (i + (role.equals("master") ? 0 : 3)) % 6 + 1,
                                        cluster.getRegion()))
                                .physicalSockets(2)
                                .physicalCores(48)
                                .threadsPerCore(2);
                    }
                    rows.add(row.build());
                }
            }
        }
        inventoryRepository.saveAll(rows);
        log.info("Loaded {} simulated inventory rows", rows.size());
    }

}
