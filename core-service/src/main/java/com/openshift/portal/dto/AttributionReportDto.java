package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.Environment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resources attributed to teams over a period. Every value is an average over the period's collections that
 * carried namespace data: a namespace's values are summed and divided by the number of those collections of its
 * cluster, so a namespace that only existed for part of the period counts for that part.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributionReportDto {
    private LocalDate from;
    private LocalDate to;
    /** Null when all environments are included. */
    private Environment environment;
    /** Namespace labels read for the owning team and the cost center. */
    private String ownerLabelKey;
    private String costCenterLabelKey;
    /** One row per team and cost center, largest CPU requests first. */
    private List<Row> teams;
    /** Namespaces without a recognised owner label; never assigned to the cluster's owner. */
    private Row unattributed;
    private Row total;
    /** Average requested CPU and memory of the same clusters over the same collections, from cluster snapshots. */
    private BigDecimal clusterCpuRequestCores;
    private BigDecimal clusterMemoryRequestGb;
    /** Namespace CPU requests as a share of the clusters' own requests over the same collections; 100 when they reconcile. */
    private BigDecimal cpuCoveragePercent;
    /** Cluster snapshots in the period, and how many of them carried namespace data (the ones averaged over). */
    private long collections;
    private long collectionsWithNamespaceData;
    /** Owner label values that matched no team, for adding teams or aliases. */
    private List<UnmappedOwner> unmappedOwners;
    /** Unattributed namespaces whose labels were read but carry no owner label. */
    private int namespacesWithoutOwnerLabel;
    /** Unattributed namespaces whose labels have never been read (no Search endpoint on their hub). */
    private int namespacesWithoutLabels;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        /** Null for the unattributed and total rows. */
        private UUID teamId;
        private String teamName;
        /** The namespaces' cost-center label, or the team's cost center when the label is absent. */
        private String costCenter;
        private int namespaceCount;
        private int clusterCount;
        private BigDecimal cpuRequestCores;
        private BigDecimal memoryRequestGb;
        /** Null when no namespace in the row has usage data. */
        private BigDecimal cpuUsageCores;
        private BigDecimal memoryUsageGb;
        private BigDecimal pvcRequestGb;
        /** Share of all attributed and unattributed CPU requests. */
        private BigDecimal cpuSharePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnmappedOwner {
        private String ownerLabelValue;
        private int namespaceCount;
    }
}
