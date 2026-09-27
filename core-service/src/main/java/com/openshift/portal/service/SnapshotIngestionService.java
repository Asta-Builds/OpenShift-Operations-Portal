package com.openshift.portal.service;

import com.openshift.portal.acm.ClusterObservation;
import com.openshift.portal.acm.NodeObservation;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns a cluster observation into a stored snapshot. Used for live collection and for the simulator's history.
 */
@Service
@RequiredArgsConstructor
public class SnapshotIngestionService {

    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeMetricsRepository;
    private final LicensingService licensingService;
    private final ProviderIdParserService providerIdParser;
    private final NamespaceIngestionService namespaceIngestionService;

    /**
     * Stores one cluster's snapshot, its namespaces and optionally its nodes, in a single short transaction.
     */
    @Transactional
    public ClusterSnapshot ingest(Cluster cluster, ClusterObservation observation, LocalDateTime timestamp, boolean withNodes) {
        List<NodeMetricsSnapshot> nodes = observation.nodes().stream()
                .map(node -> toNodeSnapshot(cluster, node, timestamp))
                .toList();

        ClusterSnapshot snapshot = snapshotRepository.save(ClusterSnapshot.builder()
                .cluster(cluster)
                .snapshotTimestamp(timestamp)
                .totalCpuCores(observation.totalCpuCores())
                .allocatedCpuCores(observation.allocatedCpuCores())
                .totalMemoryGb(observation.totalMemoryGb())
                .allocatedMemoryGb(observation.allocatedMemoryGb())
                .totalStorageGb(observation.totalStorageGb())
                .allocatedStorageGb(observation.allocatedStorageGb())
                .licenseCoresCount(licensingService.calculateLicenseCores(nodes, cluster.getInfrastructureType()))
                .totalNodes(nodes.size())
                .workerNodes((int) nodes.stream().filter(node -> node.getRole() == NodeRole.WORKER).count())
                .rawPayload(observation.rawPayload())
                .build());

        if (withNodes) {
            nodes.forEach(node -> node.setSnapshot(snapshot));
            nodeMetricsRepository.saveAll(nodes);
        }
        namespaceIngestionService.ingest(cluster, snapshot, observation.namespaces(), timestamp);
        return snapshot;
    }

    private NodeMetricsSnapshot toNodeSnapshot(Cluster cluster, NodeObservation node, LocalDateTime timestamp) {
        var providerInfo = providerIdParser.parseProviderId(node.providerId());
        return NodeMetricsSnapshot.builder()
                .cluster(cluster)
                .snapshotTimestamp(timestamp)
                .nodeName(node.name())
                .role(node.role())
                .hostType(cluster.getInfrastructureType().name())
                .cpuCores(node.cpuCores())
                .memoryGb(node.memoryGb())
                .underlyingHostId(providerInfo.getInstanceId())
                .providerId(node.providerId())
                .hypervisorHost(providerInfo.getHypervisorHost())
                .sockets(node.sockets())
                .build();
    }
}
