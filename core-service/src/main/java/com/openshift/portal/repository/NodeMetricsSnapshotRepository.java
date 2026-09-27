package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.enums.NodeRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NodeMetricsSnapshotRepository extends JpaRepository<NodeMetricsSnapshot, Long> {

    List<NodeMetricsSnapshot> findByClusterOrderBySnapshotTimestampDesc(Cluster cluster, Pageable pageable);

    List<NodeMetricsSnapshot> findBySnapshotId(Long snapshotId);

    List<NodeMetricsSnapshot> findByClusterIdAndRole(UUID clusterId, NodeRole role);

    @Query("SELECT n.hostType, COUNT(n), SUM(n.cpuCores) FROM NodeMetricsSnapshot n " +
           "WHERE n.snapshot.id = :snapshotId GROUP BY n.hostType")
    List<Object[]> summarizeHostTypesForSnapshot(@Param("snapshotId") Long snapshotId);
}
