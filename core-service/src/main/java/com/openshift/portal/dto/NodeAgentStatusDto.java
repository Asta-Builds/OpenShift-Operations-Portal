package com.openshift.portal.dto;

import java.time.LocalDateTime;

/**
 * The latest report of one cluster's node agent.
 *
 * @param registered whether an ACM hub has reported a cluster of this name
 * @param fresh      whether collections still use the report; older reports are ignored and the cluster has no nodes
 */
public record NodeAgentStatusDto(
        String clusterName,
        String agentVersion,
        LocalDateTime collectedAt,
        LocalDateTime receivedAt,
        int nodeCount,
        boolean registered,
        boolean fresh) {
}
