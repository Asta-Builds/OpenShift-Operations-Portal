package com.openshift.portal.service;

import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.enums.FinOpsEfficiencyRating;
import com.openshift.portal.domain.enums.HeadroomStatus;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.dto.*;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatIfSimulatorService {

    private static final BigDecimal HOURS_PER_MONTH = new BigDecimal("730");
    private static final BigDecimal ESTIMATED_WORKER_VM_MONTHLY_COST = new BigDecimal("180.00");
    private static final BigDecimal ESTIMATED_LICENSE_CORE_MONTHLY_COST = new BigDecimal("65.00");
    private static final int SCALE = 2;

    private final ClusterRepository clusterRepository;
    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeMetricsRepository;
    private final FinOpsService finOpsService;

    @Transactional(readOnly = true)
    public WhatIfSimulationResultDto simulate(WhatIfSimulationRequestDto request) {
        if (request == null) {
            request = new WhatIfSimulationRequestDto();
        }

        FinOpsPricingConfigDto pricing = finOpsService.getPricingConfig();
        BigDecimal cpuHourly = pricing.getCpuHourlyRate() != null ? pricing.getCpuHourlyRate() : new BigDecimal("0.045");
        BigDecimal memHourly = pricing.getMemoryHourlyRate() != null ? pricing.getMemoryHourlyRate() : new BigDecimal("0.006");
        BigDecimal storageMonthly = pricing.getStorageMonthlyRate() != null ? pricing.getStorageMonthlyRate() : new BigDecimal("0.10");

        List<Cluster> clusters = clusterRepository.findAll();
        Map<UUID, ClusterSnapshot> latestSnapshots = new HashMap<>();
        Map<UUID, List<NodeMetricsSnapshot>> latestNodes = new HashMap<>();

        for (Cluster cluster : clusters) {
            snapshotRepository.findTopByClusterOrderBySnapshotTimestampDesc(cluster)
                    .ifPresent(s -> {
                        latestSnapshots.put(cluster.getId(), s);
                        latestNodes.put(cluster.getId(), nodeMetricsRepository.findBySnapshotId(s.getId()));
                    });
        }

        // 1. Compute baseline fleet costs and allocations
        FinOpsOverviewDto overview = finOpsService.getOverview(LocalDate.now().minusDays(30), LocalDate.now(), null);
        BigDecimal baselineSpend = overview != null && overview.getTotalMonthlyAllocatedCost() != null
                ? overview.getTotalMonthlyAllocatedCost() : BigDecimal.ZERO;

        List<FinOpsNamespaceRecommendationDto> recommendations = finOpsService.getRecommendations(
                LocalDate.now().minusDays(30), LocalDate.now(), null, null, null, null);

        // Group recommendations by cluster
        Map<UUID, List<FinOpsNamespaceRecommendationDto>> recsByCluster = recommendations.stream()
                .filter(r -> r.getClusterId() != null)
                .collect(Collectors.groupingBy(FinOpsNamespaceRecommendationDto::getClusterId));

        // 2. Per-cluster simulation working state
        Map<UUID, ClusterSimState> stateMap = new HashMap<>();
        for (Cluster c : clusters) {
            ClusterSnapshot s = latestSnapshots.get(c.getId());
            int totalCores = s != null ? s.getTotalCpuCores() : 0;
            double allocCores = s != null && s.getAllocatedCpuCores() != null ? s.getAllocatedCpuCores().doubleValue() : 0.0;
            double totalMem = s != null && s.getTotalMemoryGb() != null ? s.getTotalMemoryGb().doubleValue() : 0.0;
            double allocMem = s != null && s.getAllocatedMemoryGb() != null ? s.getAllocatedMemoryGb().doubleValue() : 0.0;
            double totalStorage = totalCores * 40.0; // Standard cluster storage baseline
            double allocStorage = s != null && s.getAllocatedStorageGb() != null ? s.getAllocatedStorageGb().doubleValue() : totalStorage * 0.5;

            List<NodeMetricsSnapshot> nodes = latestNodes.getOrDefault(c.getId(), Collections.emptyList());
            long workerCount = nodes.stream().filter(n -> n.getRole() == NodeRole.WORKER).count();
            if (workerCount == 0 && totalCores > 0) {
                workerCount = Math.max(1, (totalCores - 24) / (c.getInfrastructureType() == InfrastructureType.BARE_METAL ? 32 : 16));
            }
            int coresPerWorker = c.getInfrastructureType() == InfrastructureType.BARE_METAL ? 32 : 16;
            int memPerWorker = c.getInfrastructureType() == InfrastructureType.BARE_METAL ? 128 : 64;

            stateMap.put(c.getId(), new ClusterSimState(
                    c, totalCores, allocCores, totalMem, allocMem, totalStorage, allocStorage,
                    (int) workerCount, coresPerWorker, memPerWorker
            ));
        }

        // 3. Apply Fleet Growth Factor
        if (request.getFleetGrowthPercent() != 0) {
            double factor = 1.0 + (request.getFleetGrowthPercent() / 100.0);
            for (ClusterSimState state : stateMap.values()) {
                state.simulatedAllocCores = Math.max(0, state.simulatedAllocCores * factor);
                state.simulatedAllocMem = Math.max(0, state.simulatedAllocMem * factor);
                state.simulatedAllocStorage = Math.max(0, state.simulatedAllocStorage * factor);
            }
        }

        // 4. Apply Rightsizing Adoption
        BigDecimal rightsizingSavings = BigDecimal.ZERO;
        double totalFreedCores = 0.0;
        double totalFreedMem = 0.0;

        int adoptionPercent = Math.max(0, Math.min(100, request.getRightsizingAdoptionPercent()));
        if (adoptionPercent > 0) {
            double adoptionFraction = adoptionPercent / 100.0;
            Set<FinOpsEfficiencyRating> targetRatings = (request.getTargetEfficiencyRatings() != null && !request.getTargetEfficiencyRatings().isEmpty())
                    ? new HashSet<>(request.getTargetEfficiencyRatings())
                    : EnumSet.of(FinOpsEfficiencyRating.SEVERE_WASTE, FinOpsEfficiencyRating.OVER_PROVISIONED);

            for (FinOpsNamespaceRecommendationDto rec : recommendations) {
                if (targetRatings.contains(rec.getRating())) {
                    double cpuDelta = 0.0;
                    if (rec.getAvgCpuRequestCores() != null && rec.getRecommendedCpuRequestCores() != null) {
                        cpuDelta = Math.max(0.0, rec.getAvgCpuRequestCores().subtract(rec.getRecommendedCpuRequestCores()).doubleValue());
                    }
                    double memDelta = 0.0;
                    if (rec.getAvgMemoryRequestGb() != null && rec.getRecommendedMemoryRequestGb() != null) {
                        memDelta = Math.max(0.0, rec.getAvgMemoryRequestGb().subtract(rec.getRecommendedMemoryRequestGb()).doubleValue());
                    }

                    double cpuFreed = cpuDelta * adoptionFraction;
                    double memFreed = memDelta * adoptionFraction;

                    totalFreedCores += cpuFreed;
                    totalFreedMem += memFreed;

                    if (rec.getMonthlyPotentialSavings() != null) {
                        rightsizingSavings = rightsizingSavings.add(
                                rec.getMonthlyPotentialSavings().multiply(BigDecimal.valueOf(adoptionFraction))
                        );
                    }

                    ClusterSimState clusterState = stateMap.get(rec.getClusterId());
                    if (clusterState != null) {
                        clusterState.simulatedAllocCores = Math.max(0.0, clusterState.simulatedAllocCores - cpuFreed);
                        clusterState.simulatedAllocMem = Math.max(0.0, clusterState.simulatedAllocMem - memFreed);
                    }
                }
            }
        }

        // 5. Apply Additional Workloads
        BigDecimal newWorkloadsCost = BigDecimal.ZERO;
        if (request.getAdditionalWorkloads() != null) {
            for (WhatIfWorkloadDto workload : request.getAdditionalWorkloads()) {
                ClusterSimState target = null;
                if (workload.getTargetClusterId() != null) {
                    target = stateMap.get(workload.getTargetClusterId());
                } else if (workload.getTargetClusterName() != null) {
                    target = stateMap.values().stream()
                            .filter(s -> s.cluster.getClusterName().equalsIgnoreCase(workload.getTargetClusterName()))
                            .findFirst().orElse(null);
                }

                if (target != null) {
                    target.simulatedAllocCores += workload.getRequestedCpuCores();
                    target.simulatedAllocMem += workload.getRequestedMemoryGb();
                    target.simulatedAllocStorage += workload.getRequestedStorageGb();

                    // Calculate workload cost
                    BigDecimal cpuCost = BigDecimal.valueOf(workload.getRequestedCpuCores())
                            .multiply(cpuHourly).multiply(HOURS_PER_MONTH);
                    BigDecimal memCost = BigDecimal.valueOf(workload.getRequestedMemoryGb())
                            .multiply(memHourly).multiply(HOURS_PER_MONTH);
                    BigDecimal storageCost = BigDecimal.valueOf(workload.getRequestedStorageGb())
                            .multiply(storageMonthly);

                    newWorkloadsCost = newWorkloadsCost.add(cpuCost).add(memCost).add(storageCost);
                }
            }
        }

        // 6. Apply Cluster Decommissions (Drain source into target)
        BigDecimal hardwareAndLicenseSavings = BigDecimal.ZERO;
        int totalWorkerNodesDelta = 0;
        int totalLicenseCoresDelta = 0;
        List<String> globalWarnings = new ArrayList<>();

        if (request.getClusterDecommissions() != null) {
            for (WhatIfDecommissionDto decom : request.getClusterDecommissions()) {
                ClusterSimState source = stateMap.get(decom.getSourceClusterId());
                ClusterSimState target = stateMap.get(decom.getTargetClusterId());

                if (source == null && decom.getSourceClusterName() != null) {
                    source = stateMap.values().stream()
                            .filter(s -> s.cluster.getClusterName().equalsIgnoreCase(decom.getSourceClusterName()))
                            .findFirst().orElse(null);
                }
                if (target == null && decom.getTargetClusterName() != null) {
                    target = stateMap.values().stream()
                            .filter(s -> s.cluster.getClusterName().equalsIgnoreCase(decom.getTargetClusterName()))
                            .findFirst().orElse(null);
                }

                if (source != null && target != null && !source.decommissioned) {
                    source.decommissioned = true;

                    // Transfer source allocations to target
                    target.simulatedAllocCores += source.simulatedAllocCores;
                    target.simulatedAllocMem += source.simulatedAllocMem;
                    target.simulatedAllocStorage += source.simulatedAllocStorage;

                    // Financial savings from decommissioning source cluster workers & licenses
                    int sourceWorkers = source.workerCount;
                    int sourceBillableCores = sourceWorkers * source.coresPerWorker;

                    BigDecimal vmSavings = BigDecimal.valueOf(sourceWorkers).multiply(ESTIMATED_WORKER_VM_MONTHLY_COST);
                    BigDecimal licenseSavings = BigDecimal.valueOf(sourceBillableCores).multiply(ESTIMATED_LICENSE_CORE_MONTHLY_COST);
                    BigDecimal clusterSavings = vmSavings.add(licenseSavings);

                    hardwareAndLicenseSavings = hardwareAndLicenseSavings.add(clusterSavings);
                    totalWorkerNodesDelta -= sourceWorkers;
                    totalLicenseCoresDelta -= sourceBillableCores;

                    globalWarnings.add(String.format("Cluster '%s' drained into '%s'. Freed %d worker nodes (%d cores) with $%,.2f/mo infrastructure and license savings.",
                            source.cluster.getClusterName(), target.cluster.getClusterName(), sourceWorkers, sourceBillableCores, clusterSavings));
                }
            }
        }

        // 7. Analyze Headroom, Node Optimization, and Cluster Impacts
        List<WhatIfClusterImpactDto> clusterImpacts = new ArrayList<>();
        double fleetBaselineCores = 0.0;
        double fleetSimCores = 0.0;
        double fleetTotalCores = 0.0;
        double fleetBaselineMem = 0.0;
        double fleetSimMem = 0.0;
        double fleetTotalMem = 0.0;

        List<String> strategicRecommendations = new ArrayList<>();

        for (ClusterSimState state : stateMap.values()) {
            if (state.decommissioned) {
                clusterImpacts.add(WhatIfClusterImpactDto.builder()
                        .clusterId(state.cluster.getId())
                        .clusterName(state.cluster.getClusterName())
                        .environment(state.cluster.getEnvironment())
                        .infrastructureType(state.cluster.getInfrastructureType())
                        .decommissioned(true)
                        .totalCores(state.totalCores)
                        .baselineAllocatedCores(round(state.baselineAllocCores))
                        .simulatedAllocatedCores(0.0)
                        .baselineCpuAllocPercent(round(calcPercent(state.baselineAllocCores, state.totalCores)))
                        .simulatedCpuAllocPercent(0.0)
                        .totalMemoryGb(round(state.totalMem))
                        .baselineAllocatedMemoryGb(round(state.baselineAllocMem))
                        .simulatedAllocatedMemoryGb(0.0)
                        .baselineMemoryAllocPercent(round(calcPercent(state.baselineAllocMem, state.totalMem)))
                        .simulatedMemoryAllocPercent(0.0)
                        .totalStorageGb(round(state.totalStorage))
                        .baselineAllocatedStorageGb(round(state.baselineAllocStorage))
                        .simulatedAllocatedStorageGb(0.0)
                        .headroomStatus(HeadroomStatus.OPTIMAL)
                        .statusDescription("Decommissioned & Workloads Migrated")
                        .suggestedWorkerNodeDelta(-state.workerCount)
                        .estimatedLicenseCoreDelta(-(state.workerCount * state.coresPerWorker))
                        .monthlyCostDelta(BigDecimal.valueOf(state.workerCount).multiply(ESTIMATED_WORKER_VM_MONTHLY_COST).negate())
                        .warnings(List.of("Cluster fully evacuated"))
                        .build());
                continue;
            }

            fleetBaselineCores += state.baselineAllocCores;
            fleetSimCores += state.simulatedAllocCores;
            fleetTotalCores += state.totalCores;
            fleetBaselineMem += state.baselineAllocMem;
            fleetSimMem += state.simulatedAllocMem;
            fleetTotalMem += state.totalMem;

            double cpuAllocPct = calcPercent(state.simulatedAllocCores, state.totalCores);
            double memAllocPct = calcPercent(state.simulatedAllocMem, state.totalMem);
            double maxAllocPct = Math.max(cpuAllocPct, memAllocPct);

            HeadroomStatus status;
            String statusDesc;
            int nodeDelta = 0;
            List<String> warnings = new ArrayList<>();

            if (maxAllocPct > 95.0) {
                status = HeadroomStatus.CRITICAL_OVERCOMMITTED;
                statusDesc = String.format("Critical Over-Allocation (CPU: %.1f%%, RAM: %.1f%%)", cpuAllocPct, memAllocPct);
                // Calculate required nodes to get back down to 80% safe zone
                double excessCores = Math.max(0, state.simulatedAllocCores - (state.totalCores * 0.80));
                nodeDelta = (int) Math.ceil(excessCores / state.coresPerWorker);
                if (nodeDelta == 0) nodeDelta = 1;
                warnings.add(String.format("Cluster is overcommitted. Immediate scale-out of +%d worker node(s) required to prevent scheduling bottlenecks.", nodeDelta));
            } else if (maxAllocPct > 85.0) {
                status = HeadroomStatus.WARNING_TIGHT;
                statusDesc = String.format("Tight Headroom (CPU: %.1f%%, RAM: %.1f%%)", cpuAllocPct, memAllocPct);
                warnings.add("Utilization approaches safety limit (85%). Reserve capacity is minimal.");
            } else if (maxAllocPct > 65.0) {
                status = HeadroomStatus.HEALTHY;
                statusDesc = String.format("Balanced Utilization (CPU: %.1f%%, RAM: %.1f%%)", cpuAllocPct, memAllocPct);
            } else {
                status = HeadroomStatus.OPTIMAL;
                statusDesc = String.format("High Headroom / Under-Utilized (CPU: %.1f%%, RAM: %.1f%%)", cpuAllocPct, memAllocPct);
                if (maxAllocPct < 45.0 && state.workerCount > 3) {
                    nodeDelta = -1; // Safe to downscale 1 node
                    warnings.add("Cluster has ample headroom. You can safely remove 1 worker node to reduce infrastructure and licensing spend.");
                }
            }

            int licenseCoreDelta = nodeDelta * state.coresPerWorker;
            BigDecimal nodeCostDelta = BigDecimal.valueOf(nodeDelta).multiply(ESTIMATED_WORKER_VM_MONTHLY_COST);
            if (nodeDelta < 0) {
                // Downsizing saves hardware + license
                BigDecimal licenseSavings = BigDecimal.valueOf(Math.abs(licenseCoreDelta)).multiply(ESTIMATED_LICENSE_CORE_MONTHLY_COST);
                hardwareAndLicenseSavings = hardwareAndLicenseSavings.add(nodeCostDelta.abs().add(licenseSavings));
                totalWorkerNodesDelta += nodeDelta;
                totalLicenseCoresDelta += licenseCoreDelta;
            } else if (nodeDelta > 0) {
                totalWorkerNodesDelta += nodeDelta;
                totalLicenseCoresDelta += licenseCoreDelta;
            }

            clusterImpacts.add(WhatIfClusterImpactDto.builder()
                    .clusterId(state.cluster.getId())
                    .clusterName(state.cluster.getClusterName())
                    .environment(state.cluster.getEnvironment())
                    .infrastructureType(state.cluster.getInfrastructureType())
                    .decommissioned(false)
                    .totalCores(state.totalCores)
                    .baselineAllocatedCores(round(state.baselineAllocCores))
                    .simulatedAllocatedCores(round(state.simulatedAllocCores))
                    .baselineCpuAllocPercent(round(calcPercent(state.baselineAllocCores, state.totalCores)))
                    .simulatedCpuAllocPercent(round(cpuAllocPct))
                    .totalMemoryGb(round(state.totalMem))
                    .baselineAllocatedMemoryGb(round(state.baselineAllocMem))
                    .simulatedAllocatedMemoryGb(round(state.simulatedAllocMem))
                    .baselineMemoryAllocPercent(round(calcPercent(state.baselineAllocMem, state.totalMem)))
                    .simulatedMemoryAllocPercent(round(memAllocPct))
                    .totalStorageGb(round(state.totalStorage))
                    .baselineAllocatedStorageGb(round(state.baselineAllocStorage))
                    .simulatedAllocatedStorageGb(round(state.simulatedAllocStorage))
                    .headroomStatus(status)
                    .statusDescription(statusDesc)
                    .suggestedWorkerNodeDelta(nodeDelta)
                    .estimatedLicenseCoreDelta(licenseCoreDelta)
                    .monthlyCostDelta(nodeCostDelta)
                    .warnings(warnings)
                    .build());
        }

        // 8. Financial aggregates
        BigDecimal monthlySavingsDelta = rightsizingSavings
                .add(hardwareAndLicenseSavings)
                .subtract(newWorkloadsCost)
                .setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal simulatedSpend = baselineSpend.subtract(monthlySavingsDelta).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal annualizedSavingsDelta = monthlySavingsDelta.multiply(BigDecimal.valueOf(12)).setScale(SCALE, RoundingMode.HALF_UP);

        if (adoptionPercent >= 50) {
            strategicRecommendations.add(String.format("Rightsizing at %d%% adoption unlocks $%,.2f/month ($%,.2f/year) in recoverable cloud waste.",
                    adoptionPercent, rightsizingSavings.setScale(0, RoundingMode.HALF_UP),
                    rightsizingSavings.multiply(BigDecimal.valueOf(12)).setScale(0, RoundingMode.HALF_UP)));
        }
        if (totalFreedCores > 30) {
            strategicRecommendations.add(String.format("Recovered %.0f CPU cores and %.0f GB RAM across the fleet, equivalent to ~%.1f standard worker nodes.",
                    totalFreedCores, totalFreedMem, totalFreedCores / 16.0));
        }
        if (totalWorkerNodesDelta < 0) {
            strategicRecommendations.add(String.format("Node downsizing opportunity: removing %d worker node(s) preserves $%,.2f/month in hardware and subscription license overhead.",
                    Math.abs(totalWorkerNodesDelta), hardwareAndLicenseSavings.setScale(0, RoundingMode.HALF_UP)));
        }

        return WhatIfSimulationResultDto.builder()
                .baselineMonthlySpend(baselineSpend.setScale(SCALE, RoundingMode.HALF_UP))
                .simulatedMonthlySpend(simulatedSpend)
                .monthlySavingsDelta(monthlySavingsDelta)
                .annualizedSavingsDelta(annualizedSavingsDelta)
                .rightsizingMonthlySavings(rightsizingSavings.setScale(SCALE, RoundingMode.HALF_UP))
                .hardwareAndLicenseMonthlySavings(hardwareAndLicenseSavings.setScale(SCALE, RoundingMode.HALF_UP))
                .newWorkloadsMonthlyCost(newWorkloadsCost.setScale(SCALE, RoundingMode.HALF_UP))
                .totalFreedCpuCores(round(totalFreedCores))
                .totalFreedMemoryGb(round(totalFreedMem))
                .totalFreedStorageGb(0.0)
                .baselineFleetCpuAllocPercent(round(calcPercent(fleetBaselineCores, fleetTotalCores)))
                .simulatedFleetCpuAllocPercent(round(calcPercent(fleetSimCores, fleetTotalCores)))
                .baselineFleetMemoryAllocPercent(round(calcPercent(fleetBaselineMem, fleetTotalMem)))
                .simulatedFleetMemoryAllocPercent(round(calcPercent(fleetSimMem, fleetTotalMem)))
                .totalWorkerNodesDelta(totalWorkerNodesDelta)
                .totalLicenseCoresDelta(totalLicenseCoresDelta)
                .clusterImpacts(clusterImpacts)
                .globalWarnings(globalWarnings)
                .strategicRecommendations(strategicRecommendations)
                .build();
    }

    /**
     * Curated enterprise simulation presets to test right away from the UI.
     */
    public List<WhatIfPresetDto> getPresets() {
        List<Cluster> clusters = clusterRepository.findAll();
        UUID aiClusterId = clusters.stream()
                .filter(c -> c.getClusterName().contains("ai-training"))
                .map(Cluster::getId).findFirst().orElse(null);
        UUID devClusterId = clusters.stream()
                .filter(c -> c.getClusterName().contains("dev-us-east-sandbox"))
                .map(Cluster::getId).findFirst().orElse(null);
        UUID stagingClusterId = clusters.stream()
                .filter(c -> c.getClusterName().contains("staging-us-east-01"))
                .map(Cluster::getId).findFirst().orElse(null);

        List<WhatIfPresetDto> presets = new ArrayList<>();

        presets.add(WhatIfPresetDto.builder()
                .id("preset-full-severe-waste")
                .title("Aggressive Rightsizing (100% Severe Waste)")
                .category("Cost Optimization")
                .description("Remediate 100% of severely wasted quotas across production, staging and dev. Maximizes financial recovery immediately.")
                .icon("zap")
                .badge("HIGH ROI")
                .request(WhatIfSimulationRequestDto.builder()
                        .rightsizingAdoptionPercent(100)
                        .targetEfficiencyRatings(List.of(FinOpsEfficiencyRating.SEVERE_WASTE))
                        .fleetGrowthPercent(0)
                        .build())
                .build());

        presets.add(WhatIfPresetDto.builder()
                .id("preset-balanced-enterprise")
                .title("Balanced Operations (50% Adoption)")
                .category("Pragmatic FinOps")
                .description("Realistic target: remediate 50% of severe and over-provisioned namespaces with 0% downtime risk.")
                .icon("sliders")
                .badge("RECOMMENDED")
                .request(WhatIfSimulationRequestDto.builder()
                        .rightsizingAdoptionPercent(50)
                        .targetEfficiencyRatings(List.of(FinOpsEfficiencyRating.SEVERE_WASTE, FinOpsEfficiencyRating.OVER_PROVISIONED))
                        .fleetGrowthPercent(0)
                        .build())
                .build());

        presets.add(WhatIfPresetDto.builder()
                .id("preset-onboard-genai-stack")
                .title("Onboard GenAI LLM Pipeline (+64 vCPU, +256GB)")
                .category("Capacity Planning")
                .description("Simulate landing a large LLM inference cluster workload on AI Training. Evaluates cluster headroom and node scaling.")
                .icon("cpu")
                .badge("CAPACITY CHECK")
                .request(WhatIfSimulationRequestDto.builder()
                        .rightsizingAdoptionPercent(30)
                        .additionalWorkloads(List.of(
                                WhatIfWorkloadDto.builder()
                                        .name("genai-deepseek-inference-mesh")
                                        .targetClusterId(aiClusterId)
                                        .targetClusterName("ocp-ai-training-prod")
                                        .requestedCpuCores(64.0)
                                        .requestedMemoryGb(256.0)
                                        .requestedStorageGb(1500.0)
                                        .ownerTeam("Data & AI Analytics")
                                        .build()
                        ))
                        .build())
                .build());

        if (devClusterId != null && stagingClusterId != null) {
            presets.add(WhatIfPresetDto.builder()
                    .id("preset-consolidate-sandbox")
                    .title("Consolidate Dev Sandbox into Staging")
                    .category("Infrastructure Consolidation")
                    .description("Decommission ocp-dev-us-east-sandbox and absorb its active workloads into Staging. Recovers worker nodes and OpenShift licenses.")
                    .icon("minimize-2")
                    .badge("CONSOLIDATION")
                    .request(WhatIfSimulationRequestDto.builder()
                            .rightsizingAdoptionPercent(60)
                            .clusterDecommissions(List.of(
                                    WhatIfDecommissionDto.builder()
                                            .sourceClusterId(devClusterId)
                                            .sourceClusterName("ocp-dev-us-east-sandbox")
                                            .targetClusterId(stagingClusterId)
                                            .targetClusterName("ocp-staging-us-east-01")
                                            .build()
                            ))
                            .build())
                    .build());
        }

        return presets;
    }

    private static double calcPercent(double value, double total) {
        if (total <= 0.0) return 0.0;
        return (value / total) * 100.0;
    }

    private static double round(double val) {
        return BigDecimal.valueOf(val).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static class ClusterSimState {
        final Cluster cluster;
        final int totalCores;
        final double baselineAllocCores;
        final double totalMem;
        final double baselineAllocMem;
        final double totalStorage;
        final double baselineAllocStorage;
        final int workerCount;
        final int coresPerWorker;
        final int memPerWorker;

        double simulatedAllocCores;
        double simulatedAllocMem;
        double simulatedAllocStorage;
        boolean decommissioned = false;

        ClusterSimState(Cluster cluster, int totalCores, double baselineAllocCores, double totalMem,
                        double baselineAllocMem, double totalStorage, double baselineAllocStorage,
                        int workerCount, int coresPerWorker, int memPerWorker) {
            this.cluster = cluster;
            this.totalCores = totalCores;
            this.baselineAllocCores = baselineAllocCores;
            this.totalMem = totalMem;
            this.baselineAllocMem = baselineAllocMem;
            this.totalStorage = totalStorage;
            this.baselineAllocStorage = baselineAllocStorage;
            this.workerCount = workerCount;
            this.coresPerWorker = coresPerWorker;
            this.memPerWorker = memPerWorker;

            this.simulatedAllocCores = baselineAllocCores;
            this.simulatedAllocMem = baselineAllocMem;
            this.simulatedAllocStorage = baselineAllocStorage;
        }
    }
}
