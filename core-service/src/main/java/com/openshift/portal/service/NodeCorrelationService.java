package com.openshift.portal.service;

import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.InfrastructureInventory;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.enums.ProviderType;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.InfrastructureInventoryRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Joins nodes to the infrastructure inventory on the instance key in their providerID. Hypervisor and hardware
 * facts are copied only from a matched inventory row; a node without one keeps them empty and is reported as not in
 * the inventory, never guessed.
 */
@Service
@RequiredArgsConstructor
public class NodeCorrelationService {

    /** When several sources describe the same machine, the most authoritative one wins. */
    static final List<String> SOURCE_PRIORITY = List.of("VCENTER", "BAREMETALHOST", InfrastructureInventory.SOURCE_CMDB,
            InfrastructureInventory.SOURCE_SIMULATOR);

    public enum Status {
        /** An inventory row describes the machine running the node. */
        MATCHED,
        /** A public cloud instance: the provider owns the hardware, so there is nothing to correlate. */
        CLOUD,
        /** The providerID was read, but no inventory row has its instance key. */
        NOT_IN_INVENTORY,
        /** The node has no providerID (UPI or platform "none"). */
        NO_PROVIDER_ID
    }

    private final ProviderIdParserService parser;
    private final InfrastructureInventoryRepository inventoryRepository;
    private final NodeMetricsSnapshotRepository nodeRepository;
    private final ClusterSnapshotRepository snapshotRepository;

    public static Status status(NodeMetricsSnapshot node) {
        if (node.getProviderType() == null || node.getProviderType() == ProviderType.UNKNOWN) {
            return Status.NO_PROVIDER_ID;
        }
        if (node.getInventorySource() != null) {
            return Status.MATCHED;
        }
        return node.getProviderType().isCloud() ? Status.CLOUD : Status.NOT_IN_INVENTORY;
    }

    /** Parses each node's providerID and applies the matching inventory row, if any. Does not save. */
    public void correlate(Collection<NodeMetricsSnapshot> nodes) {
        nodes.forEach(node -> {
            ProviderIdParserService.ProviderReference ref = parser.parse(node.getProviderId());
            node.setProviderType(ref.type());
            node.setUnderlyingHostId(ref.instanceKey());
            node.setProviderZone(ref.zone());
        });
        List<String> keys = nodes.stream().map(NodeMetricsSnapshot::getUnderlyingHostId).filter(Objects::nonNull).distinct().toList();
        Map<String, InfrastructureInventory> rows = keys.isEmpty() ? Map.of()
                : inventoryRepository.findByInstanceKeyIn(keys).stream()
                        .sorted(Comparator.comparingInt(row -> priority(row.getSource())))
                        .collect(Collectors.toMap(row -> row.getProviderType() + "|" + row.getInstanceKey(),
                                Function.identity(), (best, other) -> best));
        for (NodeMetricsSnapshot node : nodes) {
            apply(node, rows.get(node.getProviderType() + "|" + node.getUnderlyingHostId()));
        }
    }

    /**
     * Correlates the nodes of every cluster's latest snapshot again, so an inventory import shows up without waiting
     * for the next collection.
     *
     * @return the number of nodes now matched to an inventory row
     */
    @Transactional
    public int recorrelateLatest() {
        int matched = 0;
        for (ClusterSnapshot snapshot : snapshotRepository.findLatestSnapshotsForAllClusters()) {
            List<NodeMetricsSnapshot> nodes = nodeRepository.findBySnapshotId(snapshot.getId());
            correlate(nodes);
            nodeRepository.saveAll(nodes);
            matched += (int) nodes.stream().filter(node -> node.getInventorySource() != null).count();
        }
        return matched;
    }

    private static void apply(NodeMetricsSnapshot node, InfrastructureInventory row) {
        node.setInventorySource(row != null ? row.getSource() : null);
        node.setHypervisorHost(row != null ? row.getHypervisorHost() : null);
        node.setHypervisorCluster(row != null ? row.getHypervisorCluster() : null);
        node.setDatacenter(row != null ? row.getDatacenter() : null);
        node.setSockets(row != null ? row.getPhysicalSockets() : null);
        node.setPhysicalCores(row != null ? row.getPhysicalCores() : null);
        node.setThreadsPerCore(row != null ? row.getThreadsPerCore() : null);
    }

    private static int priority(String source) {
        int index = SOURCE_PRIORITY.indexOf(source);
        return index < 0 ? SOURCE_PRIORITY.size() : index;
    }
}
