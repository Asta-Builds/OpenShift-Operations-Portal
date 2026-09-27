package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
