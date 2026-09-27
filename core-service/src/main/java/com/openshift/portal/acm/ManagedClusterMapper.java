package com.openshift.portal.acm;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Reads the parts of an ACM {@code ManagedCluster} (cluster.open-cluster-management.io/v1) the portal uses:
 * {@code status.capacity}, the ClusterClaims in {@code status.clusterClaims}, the {@code environment} label and the
 * {@code ManagedClusterConditionAvailable} condition.
 */
public final class ManagedClusterMapper {

    static final String CLAIM_PLATFORM = "platform.open-cluster-management.io";
    static final String CLAIM_VERSION = "version.openshift.io";
    static final String CLAIM_REGION = "region.open-cluster-management.io";
    static final String CONDITION_AVAILABLE = "ManagedClusterConditionAvailable";

    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1024L * 1024 * 1024);

    private ManagedClusterMapper() {
    }

    public static ManagedClusterState map(GenericKubernetesResource cluster) {
        Map<String, Object> status = asMap(cluster.getAdditionalProperties().get("status"));
        Map<String, Object> capacity = asMap(status.get("capacity"));
        Map<String, String> labels = cluster.getMetadata().getLabels() != null ? cluster.getMetadata().getLabels() : Map.of();

        return new ManagedClusterState(
                cluster.getMetadata().getName(),
                isAvailable(status),
                cpuCores(capacity.get("cpu")),
                gigabytes(capacity.get("memory")),
                new ClusterMetadata(
                        labels.get("environment"),
                        claim(status, CLAIM_PLATFORM),
                        claim(status, CLAIM_VERSION),
                        claim(status, CLAIM_REGION)),
                Serialization.asJson(cluster));
    }

    private static boolean isAvailable(Map<String, Object> status) {
        return asList(status.get("conditions")).stream()
                .map(ManagedClusterMapper::asMap)
                .anyMatch(condition -> CONDITION_AVAILABLE.equals(condition.get("type"))
                        && "True".equals(condition.get("status")));
    }

    private static String claim(Map<String, Object> status, String name) {
        return asList(status.get("clusterClaims")).stream()
                .map(ManagedClusterMapper::asMap)
                .filter(claim -> name.equals(claim.get("name")))
                .map(claim -> (String) claim.get("value"))
                .findFirst()
                .orElse(null);
    }

    /** Quantities such as "48" or "47500m"; fractions of a core are dropped. */
    private static int cpuCores(Object quantity) {
        return quantity == null ? 0 : Quantity.getAmountInBytes(new Quantity(quantity.toString())).intValue();
    }

    /** Quantities such as "196438216Ki", in GiB. */
    static BigDecimal gigabytes(Object quantity) {
        if (quantity == null) {
            return BigDecimal.ZERO;
        }
        return Quantity.getAmountInBytes(new Quantity(quantity.toString())).divide(BYTES_PER_GB, 2, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    public record ManagedClusterState(String name, boolean available, int cpuCores, BigDecimal memoryGb,
                                      ClusterMetadata metadata, String rawJson) {
    }
}
