package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.NamespaceSnapshot;
import com.openshift.portal.domain.enums.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NamespaceSnapshotRepository extends JpaRepository<NamespaceSnapshot, Long> {
    List<NamespaceSnapshot> findByNamespaceIdOrderBySnapshotTimestampDesc(UUID namespaceId);
    Optional<NamespaceSnapshot> findTopByNamespaceIdOrderBySnapshotTimestampDesc(UUID namespaceId);

    /**
     * Per namespace in the window: snapshot count and summed values. Usage and PVC sums only cover the snapshots
     * that have a value, so their counts are returned too.
     */
    @Query("SELECT new com.openshift.portal.repository.NamespaceSnapshotRepository$NamespaceWindowTotals(" +
           "n.id, n.cluster.id, COUNT(s), SUM(s.cpuRequestCores), SUM(s.memoryRequestGb), " +
           "SUM(s.cpuUsageCores), COUNT(s.cpuUsageCores), SUM(s.memoryUsageGb), COUNT(s.memoryUsageGb), " +
           "SUM(s.pvcRequestGb), COUNT(s.pvcRequestGb)) " +
           "FROM NamespaceSnapshot s JOIN s.namespace n " +
           "WHERE s.snapshotTimestamp >= :from AND s.snapshotTimestamp < :to " +
           "AND (:environment IS NULL OR n.cluster.environment = :environment) " +
           "GROUP BY n.id, n.cluster.id")
    List<NamespaceWindowTotals> sumByNamespace(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                               @Param("environment") Environment environment);

    record NamespaceWindowTotals(UUID namespaceId, UUID clusterId, Long snapshots,
                                 BigDecimal cpuRequestCores, BigDecimal memoryRequestGb,
                                 BigDecimal cpuUsageCores, Long cpuUsageSnapshots,
                                 BigDecimal memoryUsageGb, Long memoryUsageSnapshots,
                                 BigDecimal pvcRequestGb, Long pvcSnapshots) {
    }
}
