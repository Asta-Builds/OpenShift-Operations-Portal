package com.openshift.portal.nodeagent.node;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Reads this cluster's nodes from its own API server; needs only get/list on nodes. */
@Component
@RequiredArgsConstructor
public class NodeReader {

    static final String ROLE_LABEL_PREFIX = "node-role.kubernetes.io/";

    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1024L * 1024 * 1024);

    private final KubernetesClient client;

    public List<ReportedNode> readNodes() {
        return client.nodes().list().getItems().stream()
                .map(NodeReader::toReportedNode)
                .sorted(Comparator.comparing(ReportedNode::name))
                .toList();
    }

    static ReportedNode toReportedNode(Node node) {
        Map<String, String> labels = node.getMetadata().getLabels() != null ? node.getMetadata().getLabels() : Map.of();
        Map<String, Quantity> capacity = node.getStatus() != null && node.getStatus().getCapacity() != null
                ? node.getStatus().getCapacity() : Map.of();
        return new ReportedNode(
                node.getMetadata().getName(),
                role(labels),
                cores(capacity.get("cpu")),
                gigabytes(capacity.get("memory")),
                node.getSpec() != null ? node.getSpec().getProviderID() : null);
    }

    /**
     * Infra nodes run only platform components and are exempt from subscriptions, so the infra label wins over
     * worker. Control plane nodes that are also labelled worker (compact three-node clusters) run workloads and
     * count as workers.
     */
    static NodeRole role(Map<String, String> labels) {
        boolean infra = labels.containsKey(ROLE_LABEL_PREFIX + "infra");
        boolean controlPlane = labels.containsKey(ROLE_LABEL_PREFIX + "master")
                || labels.containsKey(ROLE_LABEL_PREFIX + "control-plane");
        boolean worker = labels.containsKey(ROLE_LABEL_PREFIX + "worker");
        if (infra) {
            return NodeRole.INFRA;
        }
        if (controlPlane && !worker) {
            return NodeRole.MASTER;
        }
        return NodeRole.WORKER;
    }

    /** Whole cores, rounded up; node CPU capacity is almost always whole already. */
    static int cores(Quantity cpu) {
        if (cpu == null) {
            return 0;
        }
        return cpu.getNumericalAmount().setScale(0, RoundingMode.CEILING).intValueExact();
    }

    static BigDecimal gigabytes(Quantity memory) {
        if (memory == null) {
            return BigDecimal.ZERO;
        }
        return memory.getNumericalAmount().divide(BYTES_PER_GB, 2, RoundingMode.HALF_UP);
    }
}
