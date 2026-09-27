package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * License cores of the clusters whose nodes are known. Clusters without node data are listed apart rather than
 * counted as 0, so while any are listed every total is a lower bound.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseAuditDto {
    private int totalLicenseCores;
    private int licensedCapCores;
    private int highWatermarkCores;
    private boolean complianceBreach;
    /** BREACH as soon as the known cores exceed the cap; INCOMPLETE when they do not but some clusters are missing. */
    private ComplianceStatus complianceStatus;
    private int workerNodesCount;
    private int masterNodesCount;
    private int bareMetalCores;
    private int virtualCores;
    private Map<String, Integer> coresByEnvironment;
    private Map<String, Integer> coresByOwnerTeam;
    private Map<String, Integer> coresByInfrastructure;
    /** Clusters whose cores are in the totals. */
    private int clustersCounted;
    private List<NodeDataGap> clustersWithoutNodeData;

    public enum ComplianceStatus {
        COMPLIANT,
        BREACH,
        INCOMPLETE
    }

    public enum NodeDataGapReason {
        /** No collection of the cluster has succeeded yet. */
        NOT_COLLECTED,
        /** No node agent has reported the cluster. */
        NO_AGENT_REPORT,
        /** The agent's latest report is older than the max report age, so collections ignore it. */
        STALE_AGENT_REPORT,
        /** A fresh report arrived after the latest collection; the next collection uses it. */
        AWAITING_COLLECTION
    }

    /**
     * @param lastAgentReportAt when the portal received the cluster's latest node report, if any
     */
    public record NodeDataGap(String clusterName, String environment, NodeDataGapReason reason,
                              LocalDateTime lastAgentReportAt) {
    }
}
