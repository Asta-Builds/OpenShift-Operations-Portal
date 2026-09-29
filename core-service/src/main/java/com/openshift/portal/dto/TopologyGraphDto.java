package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopologyGraphDto {
    private List<GraphNodeDto> nodes;
    private List<GraphLinkDto> links;
    private GraphSummaryDto summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNodeDto {
        private String id;
        private String label;
        /** Type: HUB, CLUSTER, NODE, NAMESPACE, HOST */
        private String type;
        private String parentId;
        private String status;
        private String environment;
        private String team;
        private String role;
        private Double cpuCores;
        private Double memoryGb;
        private Double efficiencyPercent;
        private String rating;
        private Double monthlyCost;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphLinkDto {
        private String source;
        private String target;
        private String type;
        private double value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphSummaryDto {
        private int totalHubs;
        private int totalClusters;
        private int totalNodes;
        private int totalNamespaces;
        private int totalPhysicalHosts;
        private double totalCores;
        private double totalMemoryGb;
    }
}
