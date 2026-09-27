package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.NamespaceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NamespaceSnapshotRepository extends JpaRepository<NamespaceSnapshot, Long> {
    List<NamespaceSnapshot> findByNamespaceIdOrderBySnapshotTimestampDesc(UUID namespaceId);
    Optional<NamespaceSnapshot> findTopByNamespaceIdOrderBySnapshotTimestampDesc(UUID namespaceId);
}
