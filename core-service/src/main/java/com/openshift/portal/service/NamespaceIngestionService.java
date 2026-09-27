package com.openshift.portal.service;

import com.openshift.portal.acm.NamespaceInventory;
import com.openshift.portal.acm.NamespaceObservation;
import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.Namespace;
import com.openshift.portal.domain.entity.NamespaceSnapshot;
import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.repository.NamespaceRepository;
import com.openshift.portal.repository.NamespaceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stores the namespaces of one cluster observation: ownership from the configured labels, one snapshot per
 * namespace tied to the cluster snapshot, and deletion of namespaces that are gone.
 *
 * <p>Ownership never falls back to the cluster's owner: a namespace without a recognised owner label stays
 * unattributed. When labels could not be read, the ownership recorded earlier is kept.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NamespaceIngestionService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceSnapshotRepository namespaceSnapshotRepository;
    private final OwnerResolver ownerResolver;
    private final AcmProperties properties;

    @Transactional
    public void ingest(Cluster cluster, ClusterSnapshot clusterSnapshot, NamespaceInventory inventory,
                       LocalDateTime timestamp) {
        if (inventory == null) {
            return;
        }
        OwnerResolver.Directory directory = ownerResolver.directory();
        Map<String, Namespace> existing = namespaceRepository.findByClusterId(cluster.getId()).stream()
                .collect(Collectors.toMap(Namespace::getNamespaceName, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<NamespaceSnapshot> snapshots = new ArrayList<>();

        for (NamespaceObservation observation : inventory.namespaces()) {
            seen.add(observation.name());
            Namespace namespace = existing.get(observation.name());
            if (namespace == null) {
                namespace = Namespace.builder().cluster(cluster).namespaceName(observation.name()).build();
            }
            namespace.setLastSeenAt(timestamp);
            namespace.setDeletedAt(null);
            if (observation.labels() != null) {
                applyLabels(namespace, observation.labels(), directory);
            }
            namespace = namespaceRepository.save(namespace);

            snapshots.add(NamespaceSnapshot.builder()
                    .namespace(namespace)
                    .clusterSnapshot(clusterSnapshot)
                    .snapshotTimestamp(timestamp)
                    .cpuRequestCores(scaled(observation.cpuRequestCores(), BigDecimal.ZERO))
                    .memoryRequestGb(scaled(observation.memoryRequestGb(), BigDecimal.ZERO))
                    .cpuUsageCores(scaled(observation.cpuUsageCores(), null))
                    .memoryUsageGb(scaled(observation.memoryUsageGb(), null))
                    .pvcRequestGb(scaled(observation.pvcRequestGb(), null))
                    .build());
        }
        namespaceSnapshotRepository.saveAll(snapshots);

        if (inventory.complete()) {
            for (Namespace namespace : existing.values()) {
                if (!seen.contains(namespace.getNamespaceName()) && namespace.getDeletedAt() == null) {
                    namespace.setDeletedAt(timestamp);
                    namespaceRepository.save(namespace);
                    log.info("Namespace {} is gone from cluster {}", namespace.getNamespaceName(), cluster.getClusterName());
                }
            }
        }
    }

    /**
     * Resolves every labelled namespace's owner again, after teams or aliases changed.
     *
     * @return the number of namespaces whose team changed
     */
    @Transactional
    public int remapOwners() {
        OwnerResolver.Directory directory = ownerResolver.directory();
        int changed = 0;
        for (Namespace namespace : namespaceRepository.findByLabelsCollectedTrue()) {
            Team team = directory.resolve(namespace.getOwnerLabelValue()).orElse(null);
            if (!Objects.equals(idOf(team), idOf(namespace.getOwnerTeam()))) {
                namespace.setOwnerTeam(team);
                changed++;
            }
        }
        return changed;
    }

    private void applyLabels(Namespace namespace, Map<String, String> labels, OwnerResolver.Directory directory) {
        String owner = blankToNull(labels.get(properties.getAttribution().getOwnerLabel()));
        namespace.setOwnerLabelValue(owner);
        namespace.setCostCenter(blankToNull(labels.get(properties.getAttribution().getCostCenterLabel())));
        namespace.setOwnerTeam(directory.resolve(owner).orElse(null));
        namespace.setLabelsCollected(true);
    }

    private static Object idOf(Team team) {
        return team != null ? team.getId() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal scaled(BigDecimal value, BigDecimal whenMissing) {
        return value == null ? whenMissing : value.setScale(2, RoundingMode.HALF_UP);
    }
}
