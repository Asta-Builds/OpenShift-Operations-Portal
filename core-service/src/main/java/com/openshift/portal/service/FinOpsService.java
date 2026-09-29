package com.openshift.portal.service;

import com.openshift.portal.domain.entity.Namespace;
import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.FinOpsEfficiencyRating;
import com.openshift.portal.domain.enums.FinOpsRecommendationAction;
import com.openshift.portal.dto.*;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NamespaceRepository;
import com.openshift.portal.repository.NamespaceSnapshotRepository;
import com.openshift.portal.repository.NamespaceSnapshotRepository.NamespaceWindowTotals;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinOpsService {

    private static final BigDecimal HOURS_PER_MONTH = new BigDecimal("730");
    private static final int SCALE = 2;

    private final ClusterSnapshotRepository clusterSnapshotRepository;
    private final NamespaceSnapshotRepository namespaceSnapshotRepository;
    private final NamespaceRepository namespaceRepository;

    private final AtomicReference<FinOpsPricingConfigDto> pricingConfig =
            new AtomicReference<>(new FinOpsPricingConfigDto());

    public FinOpsPricingConfigDto getPricingConfig() {
        return pricingConfig.get();
    }

    public FinOpsPricingConfigDto updatePricingConfig(FinOpsPricingConfigDto newConfig) {
        if (newConfig == null) {
            return pricingConfig.get();
        }
        FinOpsPricingConfigDto updated = FinOpsPricingConfigDto.builder()
                .cpuHourlyRate(newConfig.getCpuHourlyRate() != null ? newConfig.getCpuHourlyRate() : new BigDecimal("0.045"))
                .memoryHourlyRate(newConfig.getMemoryHourlyRate() != null ? newConfig.getMemoryHourlyRate() : new BigDecimal("0.006"))
                .storageMonthlyRate(newConfig.getStorageMonthlyRate() != null ? newConfig.getStorageMonthlyRate() : new BigDecimal("0.10"))
                .currency(newConfig.getCurrency() != null && !newConfig.getCurrency().isBlank() ? newConfig.getCurrency() : "USD")
                .build();
        pricingConfig.set(updated);
        log.info("FinOps pricing updated: CPU=${}/h, RAM=${}/GB/h, Storage=${}/GB/mo",
                updated.getCpuHourlyRate(), updated.getMemoryHourlyRate(), updated.getStorageMonthlyRate());
        return updated;
    }

    @Transactional(readOnly = true)
    public FinOpsOverviewDto getOverview(LocalDate from, LocalDate to, Environment environment) {
        LocalDate startDay = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate endDay = to != null ? to : LocalDate.now();

        List<FinOpsNamespaceRecommendationDto> recommendations =
                computeNamespaceRecommendations(startDay, endDay, environment);

        FinOpsPricingConfigDto pricing = pricingConfig.get();

        BigDecimal totalAllocated = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalWaste = BigDecimal.ZERO;
        BigDecimal totalSavings = BigDecimal.ZERO;

        int severeCount = 0;
        int overProvCount = 0;
        int acceptableCount = 0;
        int optimalCount = 0;
        int underProvCount = 0;

        Map<String, BigDecimal> costByEnv = new HashMap<>();
        Map<String, TeamAccumulator> teamAccumulators = new HashMap<>();

        for (FinOpsNamespaceRecommendationDto rec : recommendations) {
            totalAllocated = totalAllocated.add(rec.getMonthlyAllocatedCost());
            totalActual = totalActual.add(rec.getMonthlyActualCost());
            totalWaste = totalWaste.add(rec.getMonthlyWastedCost());
            totalSavings = totalSavings.add(rec.getMonthlyPotentialSavings());

            if (rec.getEnvironment() != null) {
                costByEnv.merge(rec.getEnvironment().name(), rec.getMonthlyAllocatedCost(), BigDecimal::add);
            }

            String teamKey = rec.getTeamName() != null ? rec.getTeamName() : "Unattributed";
            TeamAccumulator acc = teamAccumulators.computeIfAbsent(teamKey,
                    k -> new TeamAccumulator(k, rec.getCostCenter()));
            acc.add(rec);

            switch (rec.getRating()) {
                case SEVERE_WASTE -> severeCount++;
                case OVER_PROVISIONED -> overProvCount++;
                case ACCEPTABLE -> acceptableCount++;
                case OPTIMAL -> optimalCount++;
                case UNDER_PROVISIONED -> underProvCount++;
            }
        }

        BigDecimal overallEfficiency = totalAllocated.compareTo(BigDecimal.ZERO) > 0
                ? totalActual.multiply(BigDecimal.valueOf(100)).divide(totalAllocated, SCALE, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100);

        BigDecimal annualizedSavings = totalSavings.multiply(BigDecimal.valueOf(12)).setScale(SCALE, RoundingMode.HALF_UP);

        final BigDecimal finalTotalAllocated = totalAllocated;
        List<FinOpsTeamBreakdownDto> teamBreakdowns = teamAccumulators.values().stream()
                .map(acc -> acc.toDto(finalTotalAllocated))
                .sorted(Comparator.comparing(FinOpsTeamBreakdownDto::getMonthlyWastedCost).reversed())
                .toList();

        List<FinOpsNamespaceRecommendationDto> topWasteful = recommendations.stream()
                .filter(r -> r.getMonthlyWastedCost().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(FinOpsNamespaceRecommendationDto::getMonthlyWastedCost).reversed())
                .limit(10)
                .toList();

        return FinOpsOverviewDto.builder()
                .from(startDay)
                .to(endDay)
                .environment(environment)
                .currency(pricing.getCurrency())
                .totalMonthlyAllocatedCost(totalAllocated.setScale(SCALE, RoundingMode.HALF_UP))
                .totalMonthlyActualCost(totalActual.setScale(SCALE, RoundingMode.HALF_UP))
                .totalMonthlyWastedCost(totalWaste.setScale(SCALE, RoundingMode.HALF_UP))
                .totalAnnualizedSavingsPotential(annualizedSavings)
                .overallFleetEfficiencyPercent(overallEfficiency)
                .totalNamespacesAnalyzed(recommendations.size())
                .severeWasteNamespacesCount(severeCount)
                .overProvisionedNamespacesCount(overProvCount)
                .acceptableNamespacesCount(acceptableCount)
                .optimalNamespacesCount(optimalCount)
                .underProvisionedNamespacesCount(underProvCount)
                .teamBreakdowns(teamBreakdowns)
                .costByEnvironment(costByEnv)
                .topWastefulNamespaces(topWasteful)
                .pricing(pricing)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FinOpsNamespaceRecommendationDto> getRecommendations(LocalDate from, LocalDate to,
                                                                      Environment environment,
                                                                      FinOpsEfficiencyRating rating,
                                                                      String teamName,
                                                                      BigDecimal minWaste) {
        LocalDate startDay = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate endDay = to != null ? to : LocalDate.now();

        List<FinOpsNamespaceRecommendationDto> list = computeNamespaceRecommendations(startDay, endDay, environment);

        return list.stream()
                .filter(r -> rating == null || r.getRating() == rating)
                .filter(r -> teamName == null || teamName.isBlank() || teamName.equalsIgnoreCase(r.getTeamName()))
                .filter(r -> minWaste == null || r.getMonthlyWastedCost().compareTo(minWaste) >= 0)
                .sorted(Comparator.comparing(FinOpsNamespaceRecommendationDto::getMonthlyWastedCost).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(LocalDate from, LocalDate to, Environment environment) {
        List<FinOpsNamespaceRecommendationDto> list = getRecommendations(from, to, environment, null, null, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8);

        // Header
        writer.println("Namespace,Cluster,Environment,Team,Cost Center,Rating,Action,CPU Req (Cores),CPU Usage (Cores),CPU Eff %,RAM Req (GB),RAM Usage (GB),RAM Eff %,Allocated Cost ($/mo),Actual Cost ($/mo),Wasted Cost ($/mo),Rec CPU (Cores),Rec RAM (GB),Monthly Savings ($)");

        for (FinOpsNamespaceRecommendationDto r : list) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.1f,%.2f,%.2f,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                    escape(r.getNamespaceName()),
                    escape(r.getClusterName()),
                    r.getEnvironment() != null ? r.getEnvironment().name() : "",
                    escape(r.getTeamName() != null ? r.getTeamName() : "Unattributed"),
                    escape(r.getCostCenter() != null ? r.getCostCenter() : "N/A"),
                    r.getRating(),
                    r.getAction(),
                    r.getAvgCpuRequestCores(),
                    r.getAvgCpuUsageCores(),
                    r.getCpuEfficiencyPercent(),
                    r.getAvgMemoryRequestGb(),
                    r.getAvgMemoryUsageGb(),
                    r.getMemoryEfficiencyPercent(),
                    r.getMonthlyAllocatedCost(),
                    r.getMonthlyActualCost(),
                    r.getMonthlyWastedCost(),
                    r.getRecommendedCpuRequestCores(),
                    r.getRecommendedMemoryRequestGb(),
                    r.getMonthlyPotentialSavings());
        }
        writer.flush();
        return out.toByteArray();
    }

    private List<FinOpsNamespaceRecommendationDto> computeNamespaceRecommendations(LocalDate from, LocalDate to, Environment environment) {
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();

        List<NamespaceWindowTotals> windowTotals = namespaceSnapshotRepository.sumByNamespace(start, end, environment);
        if (windowTotals.isEmpty()) {
            return List.of();
        }

        List<UUID> namespaceIds = windowTotals.stream().map(NamespaceWindowTotals::namespaceId).toList();
        Map<UUID, Namespace> namespaces = namespaceRepository.findWithClusterAndTeam(namespaceIds)
                .stream().collect(Collectors.toMap(Namespace::getId, Function.identity()));

        FinOpsPricingConfigDto pricing = pricingConfig.get();
        List<FinOpsNamespaceRecommendationDto> result = new ArrayList<>();

        for (NamespaceWindowTotals t : windowTotals) {
            Namespace ns = namespaces.get(t.namespaceId());
            if (ns == null || ns.getCluster() == null) {
                continue;
            }

            long snapshots = Math.max(1, t.snapshots());
            long cpuUsageSnapshots = t.cpuUsageSnapshots() != null && t.cpuUsageSnapshots() > 0 ? t.cpuUsageSnapshots() : snapshots;
            long memUsageSnapshots = t.memoryUsageSnapshots() != null && t.memoryUsageSnapshots() > 0 ? t.memoryUsageSnapshots() : snapshots;
            long pvcSnapshots = t.pvcSnapshots() != null && t.pvcSnapshots() > 0 ? t.pvcSnapshots() : snapshots;

            BigDecimal cpuReq = t.cpuRequestCores() != null
                    ? t.cpuRequestCores().divide(BigDecimal.valueOf(snapshots), SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal cpuUsage = t.cpuUsageCores() != null
                    ? t.cpuUsageCores().divide(BigDecimal.valueOf(cpuUsageSnapshots), SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal memReq = t.memoryRequestGb() != null
                    ? t.memoryRequestGb().divide(BigDecimal.valueOf(snapshots), SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal memUsage = t.memoryUsageGb() != null
                    ? t.memoryUsageGb().divide(BigDecimal.valueOf(memUsageSnapshots), SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal pvc = t.pvcRequestGb() != null
                    ? t.pvcRequestGb().divide(BigDecimal.valueOf(pvcSnapshots), SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Efficiency calculations
            BigDecimal cpuEff = cpuReq.compareTo(BigDecimal.ZERO) > 0
                    ? cpuUsage.multiply(BigDecimal.valueOf(100)).divide(cpuReq, 1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100);
            BigDecimal memEff = memReq.compareTo(BigDecimal.ZERO) > 0
                    ? memUsage.multiply(BigDecimal.valueOf(100)).divide(memReq, 1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100);

            // Costs
            BigDecimal monthlyAllocatedCpu = cpuReq.multiply(pricing.getCpuHourlyRate()).multiply(HOURS_PER_MONTH);
            BigDecimal monthlyActualCpu = cpuUsage.multiply(pricing.getCpuHourlyRate()).multiply(HOURS_PER_MONTH);

            BigDecimal monthlyAllocatedMem = memReq.multiply(pricing.getMemoryHourlyRate()).multiply(HOURS_PER_MONTH);
            BigDecimal monthlyActualMem = memUsage.multiply(pricing.getMemoryHourlyRate()).multiply(HOURS_PER_MONTH);

            BigDecimal monthlyStorage = pvc.multiply(pricing.getStorageMonthlyRate());

            BigDecimal totalAllocated = monthlyAllocatedCpu.add(monthlyAllocatedMem).add(monthlyStorage).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal totalActual = monthlyActualCpu.add(monthlyActualMem).add(monthlyStorage).setScale(SCALE, RoundingMode.HALF_UP);

            BigDecimal wastedCost = totalAllocated.subtract(totalActual).max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);

            BigDecimal overallEff = totalAllocated.compareTo(BigDecimal.ZERO) > 0
                    ? totalActual.multiply(BigDecimal.valueOf(100)).divide(totalAllocated, 1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100);

            // Recommendations logic
            FinOpsEfficiencyRating rating;
            if (cpuUsage.compareTo(cpuReq.multiply(new BigDecimal("1.05"))) > 0 ||
                memUsage.compareTo(memReq.multiply(new BigDecimal("1.05"))) > 0) {
                rating = FinOpsEfficiencyRating.UNDER_PROVISIONED;
            } else if (overallEff.compareTo(new BigDecimal("35.0")) < 0) {
                rating = FinOpsEfficiencyRating.SEVERE_WASTE;
            } else if (overallEff.compareTo(new BigDecimal("60.0")) < 0) {
                rating = FinOpsEfficiencyRating.OVER_PROVISIONED;
            } else if (overallEff.compareTo(new BigDecimal("80.0")) < 0) {
                rating = FinOpsEfficiencyRating.ACCEPTABLE;
            } else {
                rating = FinOpsEfficiencyRating.OPTIMAL;
            }

            FinOpsRecommendationAction action;
            boolean downsizeCpu = cpuEff.compareTo(new BigDecimal("70.0")) < 0 && cpuReq.compareTo(new BigDecimal("0.5")) > 0;
            boolean downsizeMem = memEff.compareTo(new BigDecimal("75.0")) < 0 && memReq.compareTo(new BigDecimal("1.0")) > 0;

            if (rating == FinOpsEfficiencyRating.UNDER_PROVISIONED) {
                action = FinOpsRecommendationAction.UPSIZE_RESOURCES;
            } else if (downsizeCpu && downsizeMem) {
                action = FinOpsRecommendationAction.DOWNSIZE_CPU_AND_RAM;
            } else if (downsizeCpu) {
                action = FinOpsRecommendationAction.DOWNSIZE_CPU;
            } else if (downsizeMem) {
                action = FinOpsRecommendationAction.DOWNSIZE_RAM;
            } else {
                action = FinOpsRecommendationAction.MAINTAIN_SIZING;
            }

            // Rightsized target: 25% headroom on CPU usage, 20% headroom on Memory usage
            BigDecimal targetCpu = downsizeCpu
                    ? cpuUsage.multiply(new BigDecimal("1.25")).setScale(SCALE, RoundingMode.HALF_UP).max(new BigDecimal("0.10"))
                    : cpuReq;
            BigDecimal targetMem = downsizeMem
                    ? memUsage.multiply(new BigDecimal("1.20")).setScale(SCALE, RoundingMode.HALF_UP).max(new BigDecimal("0.50"))
                    : memReq;

            if (rating == FinOpsEfficiencyRating.UNDER_PROVISIONED) {
                targetCpu = cpuUsage.multiply(new BigDecimal("1.30")).setScale(SCALE, RoundingMode.HALF_UP);
                targetMem = memUsage.multiply(new BigDecimal("1.25")).setScale(SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal savedCpuMonthly = cpuReq.subtract(targetCpu).max(BigDecimal.ZERO)
                    .multiply(pricing.getCpuHourlyRate()).multiply(HOURS_PER_MONTH);
            BigDecimal savedMemMonthly = memReq.subtract(targetMem).max(BigDecimal.ZERO)
                    .multiply(pricing.getMemoryHourlyRate()).multiply(HOURS_PER_MONTH);
            BigDecimal potentialSavings = savedCpuMonthly.add(savedMemMonthly).setScale(SCALE, RoundingMode.HALF_UP);

            Team team = ns.getOwnerTeam();
            String teamName = team != null ? team.getName() : "Unattributed";
            String costCenter = ns.getCostCenter() != null ? ns.getCostCenter()
                    : (team != null ? team.getCostCenter() : "N/A");

            String yamlPatch = generateResourceQuotaYaml(ns.getNamespaceName(), targetCpu, targetMem);

            result.add(FinOpsNamespaceRecommendationDto.builder()
                    .namespaceId(ns.getId())
                    .namespaceName(ns.getNamespaceName())
                    .clusterId(ns.getCluster().getId())
                    .clusterName(ns.getCluster().getClusterName())
                    .environment(ns.getCluster().getEnvironment())
                    .teamName(teamName)
                    .costCenter(costCenter)
                    .avgCpuRequestCores(cpuReq)
                    .avgCpuUsageCores(cpuUsage)
                    .cpuEfficiencyPercent(cpuEff)
                    .avgMemoryRequestGb(memReq)
                    .avgMemoryUsageGb(memUsage)
                    .memoryEfficiencyPercent(memEff)
                    .pvcRequestGb(pvc)
                    .monthlyAllocatedCost(totalAllocated)
                    .monthlyActualCost(totalActual)
                    .monthlyWastedCost(wastedCost)
                    .overallEfficiencyPercent(overallEff)
                    .rating(rating)
                    .action(action)
                    .recommendedCpuRequestCores(targetCpu)
                    .recommendedMemoryRequestGb(targetMem)
                    .monthlyPotentialSavings(potentialSavings)
                    .suggestedResourceQuotaYaml(yamlPatch)
                    .build());
        }

        return result;
    }

    private String generateResourceQuotaYaml(String namespace, BigDecimal cpu, BigDecimal mem) {
        BigDecimal cpuLimit = cpu.multiply(new BigDecimal("2.0")).setScale(1, RoundingMode.HALF_UP);
        BigDecimal memLimit = mem.multiply(new BigDecimal("1.5")).setScale(1, RoundingMode.HALF_UP);

        return String.format("""
                apiVersion: v1
                kind: ResourceQuota
                metadata:
                  name: finops-optimized-quota
                  namespace: %s
                  labels:
                    finops.openshift.io/optimized: "true"
                spec:
                  hard:
                    requests.cpu: "%s"
                    requests.memory: "%sGi"
                    limits.cpu: "%s"
                    limits.memory: "%sGi"
                """, namespace, cpu, mem, cpuLimit, memLimit);
    }

    private static String escape(String val) {
        return val != null ? val.replace("\"", "\"\"") : "";
    }

    private static class TeamAccumulator {
        private final String teamName;
        private final String costCenter;
        private int count = 0;
        private BigDecimal allocated = BigDecimal.ZERO;
        private BigDecimal actual = BigDecimal.ZERO;
        private BigDecimal waste = BigDecimal.ZERO;
        private BigDecimal savings = BigDecimal.ZERO;

        TeamAccumulator(String teamName, String costCenter) {
            this.teamName = teamName;
            this.costCenter = costCenter;
        }

        void add(FinOpsNamespaceRecommendationDto r) {
            count++;
            allocated = allocated.add(r.getMonthlyAllocatedCost());
            actual = actual.add(r.getMonthlyActualCost());
            waste = waste.add(r.getMonthlyWastedCost());
            savings = savings.add(r.getMonthlyPotentialSavings());
        }

        FinOpsTeamBreakdownDto toDto(BigDecimal totalFleetAllocated) {
            BigDecimal efficiency = allocated.compareTo(BigDecimal.ZERO) > 0
                    ? actual.multiply(BigDecimal.valueOf(100)).divide(allocated, 1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100);

            BigDecimal share = totalFleetAllocated.compareTo(BigDecimal.ZERO) > 0
                    ? allocated.multiply(BigDecimal.valueOf(100)).divide(totalFleetAllocated, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return FinOpsTeamBreakdownDto.builder()
                    .teamName(teamName)
                    .costCenter(costCenter)
                    .namespaceCount(count)
                    .monthlyAllocatedCost(allocated.setScale(SCALE, RoundingMode.HALF_UP))
                    .monthlyActualCost(actual.setScale(SCALE, RoundingMode.HALF_UP))
                    .monthlyWastedCost(waste.setScale(SCALE, RoundingMode.HALF_UP))
                    .monthlyPotentialSavings(savings.setScale(SCALE, RoundingMode.HALF_UP))
                    .costSharePercent(share)
                    .efficiencyScorePercent(efficiency)
                    .build();
        }
    }
}
