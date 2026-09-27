package com.openshift.portal.acm;

import java.math.BigDecimal;
import java.util.List;

/**
 * State of one managed cluster as read from its ACM hub. When {@code error} is set the cluster could not be
 * read and the metric fields are empty. {@code metadata} is null when the source does not describe the cluster,
 * and {@code namespaces} is null when no namespace data was read for it.
 */
public record ClusterObservation(
        String clusterName,
        int totalCpuCores,
        BigDecimal allocatedCpuCores,
        BigDecimal totalMemoryGb,
        BigDecimal allocatedMemoryGb,
        BigDecimal totalStorageGb,
        BigDecimal allocatedStorageGb,
        List<NodeObservation> nodes,
        NamespaceInventory namespaces,
        String rawPayload,
        ClusterMetadata metadata,
        String error) {

    public static ClusterObservation failed(String clusterName, String error) {
        return new ClusterObservation(clusterName, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(), null, null, null, error);
    }

    public boolean isFailed() {
        return error != null;
    }
}
