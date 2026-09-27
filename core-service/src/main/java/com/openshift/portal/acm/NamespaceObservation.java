package com.openshift.portal.acm;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One namespace of a managed cluster as read from its hub.
 *
 * @param labels namespace labels, or null when they could not be read this time (ownership is then left as it was)
 * @param cpuRequestCores     requests of running and pending pods; zero when the namespace has none
 * @param memoryRequestGb     likewise for memory
 * @param cpuUsageCores       actual consumption, or null when the source does not provide it
 * @param memoryUsageGb       working-set memory, or null when not provided
 * @param pvcRequestGb        storage requested by the namespace's PVCs, or null when not provided
 */
public record NamespaceObservation(
        String name,
        Map<String, String> labels,
        BigDecimal cpuRequestCores,
        BigDecimal memoryRequestGb,
        BigDecimal cpuUsageCores,
        BigDecimal memoryUsageGb,
        BigDecimal pvcRequestGb) {
}
