package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.enums.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClusterRepository extends JpaRepository<Cluster, UUID> {
    Optional<Cluster> findByClusterName(String clusterName);
    List<Cluster> findByEnvironment(Environment environment);
    List<Cluster> findByOwnerTeamId(UUID teamId);

    @Query("SELECT COUNT(c) FROM Cluster c")
    long countTotalClusters();

    @Query("SELECT c.environment, COUNT(c) FROM Cluster c GROUP BY c.environment")
    List<Object[]> countByEnvironmentGroup();

    @Query("SELECT c.infrastructureType, COUNT(c) FROM Cluster c GROUP BY c.infrastructureType")
    List<Object[]> countByInfrastructureTypeGroup();
}
