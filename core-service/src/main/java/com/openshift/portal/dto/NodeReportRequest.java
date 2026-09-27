package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.NodeRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Every node of one managed cluster, as read by the node agent running in it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeReportRequest {

    /** The ManagedCluster name, which is how the portal names the cluster. */
    @NotBlank
    @Size(max = 253)
    @Pattern(regexp = "[a-z0-9]([-a-z0-9.]*[a-z0-9])?", message = "must be a Kubernetes resource name")
    private String clusterName;

    @Size(max = 50)
    private String agentVersion;

    /** When the agent read the nodes. */
    @NotNull
    private Instant collectedAt;

    /** A running cluster always has at least one node, so an empty list is a broken report, not an empty cluster. */
    @NotNull
    @Size(min = 1, max = 10000)
    private List<@Valid @NotNull Node> nodes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        @NotBlank
        @Size(max = 253)
        private String name;

        @NotNull
        private NodeRole role;

        /** CPU capacity in whole cores. */
        @PositiveOrZero
        @Max(100000)
        private int cpuCores;

        /** Memory capacity in GiB. */
        @NotNull
        @PositiveOrZero
        @Digits(integer = 8, fraction = 2)
        private BigDecimal memoryGb;

        /** The node's spec.providerID, joined with the infrastructure inventory. */
        @Size(max = 255)
        private String providerId;
    }
}
