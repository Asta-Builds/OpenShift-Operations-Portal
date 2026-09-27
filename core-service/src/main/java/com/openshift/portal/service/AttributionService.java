package com.openshift.portal.service;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.Namespace;
import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.dto.AttributionReportDto;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository.ClusterWindowTotals;
import com.openshift.portal.repository.NamespaceRepository;
import com.openshift.portal.repository.NamespaceSnapshotRepository;
import com.openshift.portal.repository.NamespaceSnapshotRepository.NamespaceWindowTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rolls namespace snapshots up to teams and cost centers over a period.
 *
 * <p>Only collections that carried namespace data count. Each namespace contributes its summed values divided by
 * the number of such collections of its cluster in the period, which is its average with collections where it was
 * absent counted as zero. For each cluster these contributions add up to the cluster's average requests over the
 * same collections, so team totals reconcile with cluster-level requests.</p>
 */
@Service
@RequiredArgsConstructor
public class AttributionService {

    public static final String UNATTRIBUTED = "Unattributed";

    private static final int SCALE = 2;

    private final ClusterSnapshotRepository clusterSnapshotRepository;
    private final NamespaceSnapshotRepository namespaceSnapshotRepository;
    private final NamespaceRepository namespaceRepository;
    private final AcmProperties properties;

    /**
     * @param from        first day included
     * @param to          last day included
     * @param environment only clusters of this environment, or all when null
     */
    @Transactional(readOnly = true)
    public AttributionReportDto attribute(LocalDate from, LocalDate to, Environment environment) {
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();

        Map<UUID, ClusterWindowTotals> clusters = clusterSnapshotRepository.sumRequestsWithNamespaceData(start, end, environment)
                .stream().collect(Collectors.toMap(ClusterWindowTotals::clusterId, Function.identity()));
        long collections = clusterSnapshotRepository.countInWindow(start, end, environment);
        long collectionsWithNamespaces = clusters.values().stream().mapToLong(ClusterWindowTotals::snapshots).sum();
        List<NamespaceWindowTotals> namespaceTotals = namespaceSnapshotRepository.sumByNamespace(start, end, environment);
        Map<UUID, Namespace> namespaces = namespaceTotals.isEmpty() ? Map.of()
                : namespaceRepository.findWithClusterAndTeam(
                                namespaceTotals.stream().map(NamespaceWindowTotals::namespaceId).toList())
                        .stream().collect(Collectors.toMap(Namespace::getId, Function.identity()));

        Map<String, Accumulator> teamRows = new LinkedHashMap<>();
        Accumulator unattributed = new Accumulator(null, UNATTRIBUTED, null);
        Map<String, Integer> unmappedOwners = new TreeMap<>();
        int withoutOwnerLabel = 0;
        int withoutLabels = 0;

        for (NamespaceWindowTotals totals : namespaceTotals) {
            Namespace namespace = namespaces.get(totals.namespaceId());
            ClusterWindowTotals cluster = clusters.get(totals.clusterId());
            // A namespace snapshot always comes with a cluster snapshot; guard against data written otherwise
            long periods = Math.max(totals.snapshots(), cluster != null ? cluster.snapshots() : 0);
            Team team = namespace.getOwnerTeam();

            Accumulator row;
            if (team == null) {
                row = unattributed;
                if (!namespace.isLabelsCollected()) {
                    withoutLabels++;
                } else if (namespace.getOwnerLabelValue() == null) {
                    withoutOwnerLabel++;
                } else {
                    unmappedOwners.merge(namespace.getOwnerLabelValue(), 1, Integer::sum);
                }
            } else {
                String costCenter = namespace.getCostCenter() != null ? namespace.getCostCenter() : team.getCostCenter();
                row = teamRows.computeIfAbsent(team.getId() + "|" + costCenter,
                        key -> new Accumulator(team.getId(), team.getName(), costCenter));
            }
            row.add(namespace, totals, periods);
        }

        BigDecimal clusterCpu = BigDecimal.ZERO;
        BigDecimal clusterMemory = BigDecimal.ZERO;
        for (ClusterWindowTotals cluster : clusters.values()) {
            BigDecimal periods = BigDecimal.valueOf(cluster.snapshots());
            clusterCpu = clusterCpu.add(divide(cluster.cpuRequestCores(), periods));
            clusterMemory = clusterMemory.add(divide(cluster.memoryRequestGb(), periods));
        }

        Accumulator total = new Accumulator(null, "Total", null);
        teamRows.values().forEach(total::merge);
        total.merge(unattributed);
        BigDecimal allCpu = total.cpuRequests;

        return AttributionReportDto.builder()
                .from(from)
                .to(to)
                .environment(environment)
                .ownerLabelKey(properties.getAttribution().getOwnerLabel())
                .costCenterLabelKey(properties.getAttribution().getCostCenterLabel())
                .teams(teamRows.values().stream()
                        .sorted(Comparator.comparing((Accumulator a) -> a.cpuRequests).reversed()
                                .thenComparing(a -> a.teamName))
                        .map(a -> a.toRow(allCpu))
                        .toList())
                .unattributed(unattributed.toRow(allCpu))
                .total(total.toRow(allCpu))
                .clusterCpuRequestCores(clusterCpu.setScale(SCALE, RoundingMode.HALF_UP))
                .clusterMemoryRequestGb(clusterMemory.setScale(SCALE, RoundingMode.HALF_UP))
                .cpuCoveragePercent(clusterCpu.signum() == 0 ? null
                        : allCpu.multiply(BigDecimal.valueOf(100)).divide(clusterCpu, 1, RoundingMode.HALF_UP))
                .collections(collections)
                .collectionsWithNamespaceData(collectionsWithNamespaces)
                .unmappedOwners(unmappedOwners.entrySet().stream()
                        .map(e -> new AttributionReportDto.UnmappedOwner(e.getKey(), e.getValue()))
                        .toList())
                .namespacesWithoutOwnerLabel(withoutOwnerLabel)
                .namespacesWithoutLabels(withoutLabels)
                .build();
    }

    /** Values are kept unrounded while summing and rounded once per row. */
    private static BigDecimal divide(BigDecimal value, BigDecimal by) {
        return value == null || by.signum() == 0 ? BigDecimal.ZERO : value.divide(by, 10, RoundingMode.HALF_UP);
    }

    private static final class Accumulator {
        private final UUID teamId;
        private final String teamName;
        private final String costCenter;
        private final Set<UUID> namespaceIds = new HashSet<>();
        private final Set<UUID> clusterIds = new HashSet<>();
        private BigDecimal cpuRequests = BigDecimal.ZERO;
        private BigDecimal memoryRequests = BigDecimal.ZERO;
        private BigDecimal cpuUsage;
        private BigDecimal memoryUsage;
        private BigDecimal pvcRequests;

        private Accumulator(UUID teamId, String teamName, String costCenter) {
            this.teamId = teamId;
            this.teamName = teamName;
            this.costCenter = costCenter;
        }

        private void add(Namespace namespace, NamespaceWindowTotals totals, long periods) {
            BigDecimal n = BigDecimal.valueOf(periods);
            namespaceIds.add(namespace.getId());
            clusterIds.add(namespace.getCluster().getId());
            cpuRequests = cpuRequests.add(divide(totals.cpuRequestCores(), n));
            memoryRequests = memoryRequests.add(divide(totals.memoryRequestGb(), n));
            cpuUsage = plus(cpuUsage, partial(totals.cpuUsageCores(), totals.cpuUsageSnapshots(), totals.snapshots(), n));
            memoryUsage = plus(memoryUsage, partial(totals.memoryUsageGb(), totals.memoryUsageSnapshots(), totals.snapshots(), n));
            pvcRequests = plus(pvcRequests, partial(totals.pvcRequestGb(), totals.pvcSnapshots(), totals.snapshots(), n));
        }

        private void merge(Accumulator other) {
            namespaceIds.addAll(other.namespaceIds);
            clusterIds.addAll(other.clusterIds);
            cpuRequests = cpuRequests.add(other.cpuRequests);
            memoryRequests = memoryRequests.add(other.memoryRequests);
            cpuUsage = plus(cpuUsage, other.cpuUsage);
            memoryUsage = plus(memoryUsage, other.memoryUsage);
            pvcRequests = plus(pvcRequests, other.pvcRequests);
        }

        /**
         * A value known for only some of the namespace's snapshots is averaged over those, then weighted by the
         * share of the period the namespace existed. Null when never known.
         */
        private static BigDecimal partial(BigDecimal sum, Long known, long present, BigDecimal periods) {
            if (sum == null || known == null || known == 0) {
                return null;
            }
            BigDecimal average = divide(sum, BigDecimal.valueOf(known));
            return average.multiply(BigDecimal.valueOf(present)).divide(periods, 10, RoundingMode.HALF_UP);
        }

        private static BigDecimal plus(BigDecimal a, BigDecimal b) {
            if (a == null) {
                return b;
            }
            return b == null ? a : a.add(b);
        }

        private AttributionReportDto.Row toRow(BigDecimal allCpu) {
            return AttributionReportDto.Row.builder()
                    .teamId(teamId)
                    .teamName(teamName)
                    .costCenter(costCenter)
                    .namespaceCount(namespaceIds.size())
                    .clusterCount(clusterIds.size())
                    .cpuRequestCores(rounded(cpuRequests))
                    .memoryRequestGb(rounded(memoryRequests))
                    .cpuUsageCores(rounded(cpuUsage))
                    .memoryUsageGb(rounded(memoryUsage))
                    .pvcRequestGb(rounded(pvcRequests))
                    .cpuSharePercent(allCpu.signum() == 0 ? BigDecimal.ZERO
                            : cpuRequests.multiply(BigDecimal.valueOf(100)).divide(allCpu, 1, RoundingMode.HALF_UP))
                    .build();
        }

        private static BigDecimal rounded(BigDecimal value) {
            return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
        }
    }
}
