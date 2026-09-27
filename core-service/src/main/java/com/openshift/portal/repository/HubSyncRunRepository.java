package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.HubSyncRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HubSyncRunRepository extends JpaRepository<HubSyncRun, Long> {
    Optional<HubSyncRun> findTopByHubIdOrderByIdDesc(UUID hubId);
    List<HubSyncRun> findByHubIdOrderByIdDesc(UUID hubId, Pageable pageable);
}
