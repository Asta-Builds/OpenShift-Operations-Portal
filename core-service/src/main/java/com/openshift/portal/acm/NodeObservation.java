package com.openshift.portal.acm;

import com.openshift.portal.domain.enums.NodeRole;

import java.math.BigDecimal;

/**
 * One node of a managed cluster as reported by its hub; {@code sockets} is null when the platform does not expose it.
 */
public record NodeObservation(
        String name,
        NodeRole role,
        int cpuCores,
        BigDecimal memoryGb,
        String providerId,
        Integer sockets) {
}
