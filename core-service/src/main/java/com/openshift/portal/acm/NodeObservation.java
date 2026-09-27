package com.openshift.portal.acm;

import com.openshift.portal.domain.enums.NodeRole;

import java.math.BigDecimal;

/**
 * One node of a managed cluster as reported by its hub. Kubernetes does not report sockets or physical cores; those
 * come from the infrastructure inventory.
 */
public record NodeObservation(
        String name,
        NodeRole role,
        int cpuCores,
        BigDecimal memoryGb,
        String providerId) {
}
