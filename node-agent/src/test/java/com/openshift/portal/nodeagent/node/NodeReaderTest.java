package com.openshift.portal.nodeagent.node;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class NodeReaderTest {

    KubernetesClient client;

    @Test
    void readsEveryNodeWithRoleCapacityAndProviderId() {
        client.nodes().resource(node("worker-b", Map.of("node-role.kubernetes.io/worker", ""), "16", "64Gi",
                "vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c")).create();
        client.nodes().resource(node("master-0", Map.of("node-role.kubernetes.io/master", "",
                "node-role.kubernetes.io/control-plane", ""), "8", "32Gi", "aws:///eu-west-1a/i-0a1b2c3d4e5f")).create();
        client.nodes().resource(node("infra-0", Map.of("node-role.kubernetes.io/infra", "",
                "node-role.kubernetes.io/worker", ""), "4", "16Gi", null)).create();

        List<ReportedNode> nodes = new NodeReader(client).readNodes();

        assertThat(nodes).containsExactly(
                new ReportedNode("infra-0", NodeRole.INFRA, 4, new BigDecimal("16.00"), null),
                new ReportedNode("master-0", NodeRole.MASTER, 8, new BigDecimal("32.00"), "aws:///eu-west-1a/i-0a1b2c3d4e5f"),
                new ReportedNode("worker-b", NodeRole.WORKER, 16, new BigDecimal("64.00"),
                        "vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c"));
    }

    @Test
    void compactClusterControlPlaneNodesRunWorkloadsAndCountAsWorkers() {
        assertThat(NodeReader.role(Map.of("node-role.kubernetes.io/master", "", "node-role.kubernetes.io/worker", "")))
                .isEqualTo(NodeRole.WORKER);
        assertThat(NodeReader.role(Map.of("node-role.kubernetes.io/control-plane", ""))).isEqualTo(NodeRole.MASTER);
        assertThat(NodeReader.role(Map.of("node-role.kubernetes.io/infra", "", "node-role.kubernetes.io/master", "")))
                .isEqualTo(NodeRole.INFRA);
        // Plain Kubernetes workers often carry no role label at all
        assertThat(NodeReader.role(Map.of("kubernetes.io/os", "linux"))).isEqualTo(NodeRole.WORKER);
    }

    @Test
    void convertsQuantitiesToWholeCoresAndGigabytes() {
        assertThat(NodeReader.cores(new Quantity("7500m"))).isEqualTo(8);
        assertThat(NodeReader.cores(new Quantity("48"))).isEqualTo(48);
        assertThat(NodeReader.cores(null)).isZero();
        assertThat(NodeReader.gigabytes(new Quantity("65843220Ki"))).isEqualByComparingTo("62.79");
        assertThat(NodeReader.gigabytes(null)).isEqualByComparingTo("0");
    }

    @Test
    void nodeWithoutStatusOrLabelsIsStillReported() {
        Node bare = new NodeBuilder().withNewMetadata().withName("bare").endMetadata().build();

        assertThat(NodeReader.toReportedNode(bare))
                .isEqualTo(new ReportedNode("bare", NodeRole.WORKER, 0, BigDecimal.ZERO, null));
    }

    private static Node node(String name, Map<String, String> labels, String cpu, String memory, String providerId) {
        return new NodeBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec().withProviderID(providerId).endSpec()
                .withNewStatus().withCapacity(Map.of("cpu", new Quantity(cpu), "memory", new Quantity(memory))).endStatus()
                .build();
    }
}
