package com.openshift.portal.service;

import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.dto.InfrastructureTopologyDto;
import com.openshift.portal.dto.InfrastructureTopologyDto.Node;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Builds the infrastructure topology from the nodes of every cluster's latest snapshot. */
@Service
@RequiredArgsConstructor
public class InfrastructureTopologyService {

    private static final String NONE = "";
    private static final Comparator<Node> BY_NAME = Comparator.comparing(Node::clusterName).thenComparing(Node::nodeName);

    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeRepository;

    @Transactional(readOnly = true)
    public InfrastructureTopologyDto topology() {
        List<NodeMetricsSnapshot> nodes = new ArrayList<>();
        for (ClusterSnapshot snapshot : snapshotRepository.findLatestSnapshotsForAllClusters()) {
            nodes.addAll(nodeRepository.findBySnapshotId(snapshot.getId()));
        }

        // datacenter -> hypervisor cluster -> host -> nodes
        Map<String, Map<String, Map<String, List<NodeMetricsSnapshot>>>> virtual = new TreeMap<>();
        Map<String, List<Node>> bareMetal = new TreeMap<>();
        Map<String, List<Node>> cloud = new TreeMap<>();
        List<Node> notInInventory = new ArrayList<>();
        List<Node> noProviderId = new ArrayList<>();

        for (NodeMetricsSnapshot node : nodes) {
            NodeCorrelationService.Status status = NodeCorrelationService.status(node);
            switch (status) {
                case MATCHED -> {
                    if (node.getHypervisorHost() != null) {
                        virtual.computeIfAbsent(orNone(node.getDatacenter()), k -> new TreeMap<>())
                                .computeIfAbsent(orNone(node.getHypervisorCluster()), k -> new TreeMap<>())
                                .computeIfAbsent(node.getHypervisorHost(), k -> new ArrayList<>())
                                .add(node);
                    } else {
                        bareMetal.computeIfAbsent(orNone(node.getDatacenter()), k -> new ArrayList<>()).add(toNode(node));
                    }
                }
                case CLOUD -> cloud.computeIfAbsent(node.getProviderType() + "|" + orNone(node.getProviderZone()),
                        k -> new ArrayList<>()).add(toNode(node));
                case NOT_IN_INVENTORY -> notInInventory.add(toNode(node));
                case NO_PROVIDER_ID -> noProviderId.add(toNode(node));
            }
        }

        List<InfrastructureTopologyDto.HypervisorCluster> hypervisorClusters = new ArrayList<>();
        virtual.forEach((datacenter, clusters) -> clusters.forEach((cluster, hosts) -> {
            List<InfrastructureTopologyDto.Host> hostDtos = new ArrayList<>();
            hosts.forEach((host, vms) -> {
                NodeMetricsSnapshot first = vms.get(0);
                hostDtos.add(new InfrastructureTopologyDto.Host(host, first.getSockets(), first.getPhysicalCores(),
                        first.getThreadsPerCore(), first.getInventorySource(),
                        vms.stream().map(InfrastructureTopologyService::toNode).sorted(BY_NAME).toList()));
            });
            hypervisorClusters.add(new InfrastructureTopologyDto.HypervisorCluster(noneToNull(datacenter), noneToNull(cluster), hostDtos));
        }));

        int matched = (int) nodes.stream().filter(n -> n.getInventorySource() != null).count();
        return new InfrastructureTopologyDto(
                new InfrastructureTopologyDto.Summary(nodes.size(), matched,
                        cloud.values().stream().mapToInt(List::size).sum(), notInInventory.size(), noProviderId.size()),
                hypervisorClusters,
                bareMetal.entrySet().stream()
                        .map(e -> new InfrastructureTopologyDto.Datacenter(noneToNull(e.getKey()), sorted(e.getValue())))
                        .toList(),
                cloud.entrySet().stream().map(e -> {
                    Node first = e.getValue().get(0);
                    return new InfrastructureTopologyDto.CloudZone(first.providerType(), first.zone(), sorted(e.getValue()));
                }).toList(),
                sorted(notInInventory),
                sorted(noProviderId));
    }

    private static Node toNode(NodeMetricsSnapshot n) {
        return new Node(n.getCluster().getId(), n.getCluster().getClusterName(), n.getNodeName(), n.getRole(),
                n.getCpuCores(), n.getMemoryGb(), n.getProviderType(), n.getUnderlyingHostId(), n.getProviderZone(),
                NodeCorrelationService.status(n), n.getInventorySource(), n.getSockets(), n.getPhysicalCores(),
                n.getThreadsPerCore());
    }

    private static List<Node> sorted(List<Node> nodes) {
        return nodes.stream().sorted(BY_NAME).toList();
    }

    private static String orNone(String value) {
        return Objects.requireNonNullElse(value, NONE);
    }

    private static String noneToNull(String value) {
        return NONE.equals(value) ? null : value;
    }
}
