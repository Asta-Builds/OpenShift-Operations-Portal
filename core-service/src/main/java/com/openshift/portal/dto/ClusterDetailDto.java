package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterDetailDto {
    private UUID id;
    private String clusterName;
    private String acmHubName;
    private Environment environment;
    private String ownerTeamName;
    private String costCenter;
    private InfrastructureType infrastructureType;
    private String openshiftVersion;
    private String region;
    private String status;
    private SnapshotDto latestSnapshot;
    private List<NodeMetricDto> nodeMetrics;
    /** The namespace label that names the owning team. */
    private String ownerLabelKey;
    private List<NamespaceSummaryDto> namespaces;
    private List<SnapshotDto> recentSnapshots;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotDto {
        private Long id;
        private LocalDateTime snapshotTimestamp;
        private Integer totalCpuCores;
        private Double allocatedCpuCores;
        private Double totalMemoryGb;
        private Double allocatedMemoryGb;
        private Double totalStorageGb;
        private Double allocatedStorageGb;
        private Integer licenseCoresCount;
        private Integer totalNodes;
        private Integer workerNodes;
        private String rawPayload;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeMetricDto {
        private Long id;
        private String nodeName;
        private NodeRole role;
        private String hostType;
        private Integer cpuCores;
        private Double memoryGb;
        private String underlyingHostId;
        private String providerId;
        private String hypervisorHost;
        private Integer sockets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NamespaceSummaryDto {
        private UUID id;
        private String namespaceName;
        /** The owning team, or "Unattributed"; never the cluster's owner by default. */
        private String ownerTeamName;
        private boolean attributed;
        /** Raw owner label value, shown even when it matches no team. */
        private String ownerLabelValue;
        /** False until the namespace's labels have been read. */
        private boolean labelsCollected;
        private String costCenter;
        /** LABEL when the namespace carries a cost-center label, TEAM when the team's cost center applies. */
        private String costCenterSource;
        private Double cpuRequestCores;
        private Double memoryRequestGb;
        private Double cpuUsageCores;
        private Double memoryUsageGb;
        private Double pvcRequestGb;
        private LocalDateTime lastSeenAt;
        private LocalDateTime deletedAt;
    }
}
