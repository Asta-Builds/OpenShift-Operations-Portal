package com.openshift.portal.service;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.dto.FinOpsNamespaceRecommendationDto;
import com.openshift.portal.dto.InfrastructureTopologyDto;
import com.openshift.portal.dto.InfrastructureTopologyDto.Node;
import com.openshift.portal.dto.TopologyGraphDto;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Builds the infrastructure topology from the nodes of every cluster's latest snapshot. */
@Service
@RequiredArgsConstructor
public class InfrastructureTopologyService {

    private static final String NONE = "";
    private static final Comparator<Node> BY_NAME = Comparator.comparing(Node::clusterName).thenComparing(Node::nodeName);

    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeRepository;
    private final AcmHubRepository acmHubRepository;
    private final ClusterRepository clusterRepository;
    private final FinOpsService finOpsService;

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

    @Transactional(readOnly = true)
    public TopologyGraphDto buildTopologyGraph() {
        List<TopologyGraphDto.GraphNodeDto> graphNodes = new ArrayList<>();
        List<TopologyGraphDto.GraphLinkDto> graphLinks = new ArrayList<>();

        List<AcmHub> hubs = acmHubRepository.findAll();
        List<Cluster> clusters = clusterRepository.findAll();

        int totalNodesCount = 0;
        double totalFleetCores = 0.0;
        double totalFleetMem = 0.0;

        // 1. ACM Hubs
        for (AcmHub hub : hubs) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("apiUrl", hub.getApiUrl() != null ? hub.getApiUrl() : "");
            meta.put("clusterCount", hub.getClusters() != null ? hub.getClusters().size() : 0);

            graphNodes.add(TopologyGraphDto.GraphNodeDto.builder()
                    .id("hub-" + hub.getId())
                    .label(hub.getName())
                    .type("HUB")
                    .status(hub.getStatus() != null ? hub.getStatus().name() : "ACTIVE")
                    .metadata(meta)
                    .build());
        }

        // 2. Clusters and their Nodes
        for (Cluster cluster : clusters) {
            String hubId = cluster.getAcmHub() != null ? "hub-" + cluster.getAcmHub().getId() : null;
            Optional<ClusterSnapshot> latestSnapshot = snapshotRepository.findTopByClusterOrderBySnapshotTimestampDesc(cluster);

            int clusterCores = latestSnapshot.map(ClusterSnapshot::getTotalCpuCores).orElse(0);
            double clusterAllocCores = latestSnapshot.map(s -> s.getAllocatedCpuCores() != null ? s.getAllocatedCpuCores().doubleValue() : 0.0).orElse(0.0);
            double clusterMem = latestSnapshot.map(s -> s.getTotalMemoryGb() != null ? s.getTotalMemoryGb().doubleValue() : 0.0).orElse(0.0);
            double clusterAllocMem = latestSnapshot.map(s -> s.getAllocatedMemoryGb() != null ? s.getAllocatedMemoryGb().doubleValue() : 0.0).orElse(0.0);

            totalFleetCores += clusterCores;
            totalFleetMem += clusterMem;

            double cpuPct = clusterCores > 0 ? (clusterAllocCores / clusterCores) * 100.0 : 0.0;

            String clusterNodeId = "cluster-" + cluster.getId();
            Map<String, Object> clusterMeta = new HashMap<>();
            clusterMeta.put("infraType", cluster.getInfrastructureType() != null ? cluster.getInfrastructureType().name() : "");
            clusterMeta.put("version", cluster.getOpenshiftVersion() != null ? cluster.getOpenshiftVersion() : "");
            clusterMeta.put("allocatedCores", clusterAllocCores);
            clusterMeta.put("allocatedMemoryGb", clusterAllocMem);

            graphNodes.add(TopologyGraphDto.GraphNodeDto.builder()
                    .id(clusterNodeId)
                    .label(cluster.getClusterName())
                    .type("CLUSTER")
                    .parentId(hubId)
                    .environment(cluster.getEnvironment() != null ? cluster.getEnvironment().name() : "PRODUCTION")
                    .status(cpuPct > 85.0 ? "WARNING" : "HEALTHY")
                    .team(cluster.getOwnerTeam() != null ? cluster.getOwnerTeam().getName() : "Unassigned")
                    .cpuCores((double) clusterCores)
                    .memoryGb(clusterMem)
                    .efficiencyPercent(cpuPct)
                    .metadata(clusterMeta)
                    .build());

            if (hubId != null) {
                graphLinks.add(TopologyGraphDto.GraphLinkDto.builder()
                        .source(hubId)
                        .target(clusterNodeId)
                        .type("HUB_TO_CLUSTER")
                        .value(3.0)
                        .build());
            }

            // Cluster Nodes
            if (latestSnapshot.isPresent()) {
                List<NodeMetricsSnapshot> nodes = nodeRepository.findBySnapshotId(latestSnapshot.get().getId());
                totalNodesCount += nodes.size();

                for (NodeMetricsSnapshot node : nodes) {
                    String nodeGraphId = "node-" + node.getId();
                    Map<String, Object> nodeMeta = new HashMap<>();
                    nodeMeta.put("hostType", node.getHostType() != null ? node.getHostType() : "");
                    nodeMeta.put("hypervisorHost", node.getHypervisorHost() != null ? node.getHypervisorHost() : "");

                    graphNodes.add(TopologyGraphDto.GraphNodeDto.builder()
                            .id(nodeGraphId)
                            .label(node.getNodeName())
                            .type("NODE")
                            .parentId(clusterNodeId)
                            .role(node.getRole() != null ? node.getRole().name() : "WORKER")
                            .status("HEALTHY")
                            .cpuCores(node.getCpuCores() != null ? node.getCpuCores().doubleValue() : 0.0)
                            .memoryGb(node.getMemoryGb() != null ? node.getMemoryGb().doubleValue() : 0.0)
                            .metadata(nodeMeta)
                            .build());

                    graphLinks.add(TopologyGraphDto.GraphLinkDto.builder()
                            .source(clusterNodeId)
                            .target(nodeGraphId)
                            .type("CLUSTER_TO_NODE")
                            .value(1.5)
                            .build());
                }
            }
        }

        // 3. Namespaces from FinOps
        List<FinOpsNamespaceRecommendationDto> recommendations = finOpsService.getRecommendations(null, null, null, null, null, null);
        for (FinOpsNamespaceRecommendationDto rec : recommendations) {
            String clusterNodeId = "cluster-" + rec.getClusterId();
            String nsNodeId = "ns-" + rec.getNamespaceId();

            Map<String, Object> nsMeta = new HashMap<>();
            nsMeta.put("costCenter", rec.getCostCenter() != null ? rec.getCostCenter() : "");
            nsMeta.put("wastedCost", rec.getMonthlyWastedCost() != null ? rec.getMonthlyWastedCost().doubleValue() : 0.0);
            nsMeta.put("action", rec.getAction() != null ? rec.getAction().name() : "");

            graphNodes.add(TopologyGraphDto.GraphNodeDto.builder()
                    .id(nsNodeId)
                    .label(rec.getNamespaceName())
                    .type("NAMESPACE")
                    .parentId(clusterNodeId)
                    .environment(rec.getEnvironment() != null ? rec.getEnvironment().name() : "PRODUCTION")
                    .team(rec.getTeamName())
                    .rating(rec.getRating() != null ? rec.getRating().name() : "ACCEPTABLE")
                    .efficiencyPercent(rec.getOverallEfficiencyPercent() != null ? rec.getOverallEfficiencyPercent().doubleValue() : 50.0)
                    .monthlyCost(rec.getMonthlyAllocatedCost() != null ? rec.getMonthlyAllocatedCost().doubleValue() : 0.0)
                    .metadata(nsMeta)
                    .build());

            graphLinks.add(TopologyGraphDto.GraphLinkDto.builder()
                    .source(clusterNodeId)
                    .target(nsNodeId)
                    .type("CLUSTER_TO_NAMESPACE")
                    .value(1.0)
                    .build());
        }

        TopologyGraphDto.GraphSummaryDto summary = TopologyGraphDto.GraphSummaryDto.builder()
                .totalHubs(hubs.size())
                .totalClusters(clusters.size())
                .totalNodes(totalNodesCount)
                .totalNamespaces(recommendations.size())
                .totalCores(totalFleetCores)
                .totalMemoryGb(totalFleetMem)
                .build();

        return TopologyGraphDto.builder()
                .nodes(graphNodes)
                .links(graphLinks)
                .summary(summary)
                .build();
    }
}
