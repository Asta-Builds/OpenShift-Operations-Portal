package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.enums.Environment;
import org.springframework.data.domain.Pageable;
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
public interface ClusterSnapshotRepository extends JpaRepository<ClusterSnapshot, Long> {

    Optional<ClusterSnapshot> findTopByClusterOrderBySnapshotTimestampDesc(Cluster cluster);

    List<ClusterSnapshot> findByClusterOrderBySnapshotTimestampDesc(Cluster cluster, Pageable pageable);

    List<ClusterSnapshot> findByClusterIdAndSnapshotTimestampBetweenOrderBySnapshotTimestampAsc(
            UUID clusterId, LocalDateTime start, LocalDateTime end);

    List<ClusterSnapshot> findBySnapshotTimestampBetweenOrderBySnapshotTimestampAsc(
            LocalDateTime start, LocalDateTime end);

    @Query("SELECT s FROM ClusterSnapshot s WHERE s.id IN (" +
           "  SELECT MAX(s2.id) FROM ClusterSnapshot s2 GROUP BY s2.cluster.id" +
           ")")
    List<ClusterSnapshot> findLatestSnapshotsForAllClusters();

    @Query("SELECT SUM(s.licenseCoresCount) FROM ClusterSnapshot s WHERE s.id IN (" +
           "  SELECT MAX(s2.id) FROM ClusterSnapshot s2 GROUP BY s2.cluster.id" +
           ")")
    Integer sumLatestLicenseCoresCount();

    @Query("SELECT SUM(s.totalCpuCores), SUM(s.allocatedCpuCores), SUM(s.totalMemoryGb), SUM(s.allocatedMemoryGb), " +
           "SUM(s.totalStorageGb), SUM(s.allocatedStorageGb) " +
           "FROM ClusterSnapshot s WHERE s.id IN (" +
           "  SELECT MAX(s2.id) FROM ClusterSnapshot s2 GROUP BY s2.cluster.id" +
           ")")
    List<Object[]> sumLatestFleetCapacity();

    /**
     * Per cluster in the window, over the snapshots collected together with namespace data: their count and summed
     * requests. Attribution averages over exactly these collections, so its totals compare like for like.
     */
    @Query("SELECT new com.openshift.portal.repository.ClusterSnapshotRepository$ClusterWindowTotals(" +
           "s.cluster.id, COUNT(s), SUM(s.allocatedCpuCores), SUM(s.allocatedMemoryGb)) " +
           "FROM ClusterSnapshot s " +
           "WHERE s.snapshotTimestamp >= :from AND s.snapshotTimestamp < :to " +
           "AND (:environment IS NULL OR s.cluster.environment = :environment) " +
           "AND EXISTS (SELECT 1 FROM NamespaceSnapshot n WHERE n.clusterSnapshot = s) " +
           "GROUP BY s.cluster.id")
    List<ClusterWindowTotals> sumRequestsWithNamespaceData(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                                            @Param("environment") Environment environment);

    @Query("SELECT COUNT(s) FROM ClusterSnapshot s " +
           "WHERE s.snapshotTimestamp >= :from AND s.snapshotTimestamp < :to " +
           "AND (:environment IS NULL OR s.cluster.environment = :environment)")
    long countInWindow(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                       @Param("environment") Environment environment);

    record ClusterWindowTotals(UUID clusterId, Long snapshots, Long cpuRequestCores, BigDecimal memoryRequestGb) {
    }
}
