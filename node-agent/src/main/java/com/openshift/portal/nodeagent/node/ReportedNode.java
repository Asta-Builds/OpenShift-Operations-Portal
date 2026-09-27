package com.openshift.portal.nodeagent.node;

import java.math.BigDecimal;

/**
 * One node as sent to the portal. CPU and memory are the node's capacity, which is what the machine provides;
 * {@code providerId} is {@code spec.providerID}, which the portal joins with its infrastructure inventory.
 */
public record ReportedNode(
        String name,
        NodeRole role,
        int cpuCores,
        BigDecimal memoryGb,
        String providerId) {
}
