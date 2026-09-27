package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.Namespace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NamespaceRepository extends JpaRepository<Namespace, UUID> {
    Optional<Namespace> findByClusterIdAndNamespaceName(UUID clusterId, String namespaceName);
    List<Namespace> findByClusterId(UUID clusterId);
    List<Namespace> findByOwnerTeamId(UUID teamId);
    List<Namespace> findByLabelsCollectedTrue();
    long countByOwnerTeamIdAndDeletedAtIsNull(UUID teamId);

    /** Namespaces with their cluster and owning team, for attribution. */
    @Query("select n from Namespace n join fetch n.cluster left join fetch n.ownerTeam where n.id in :ids")
    List<Namespace> findWithClusterAndTeam(Collection<UUID> ids);
}
