package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.enums.HubStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcmHubRepository extends JpaRepository<AcmHub, UUID> {
    Optional<AcmHub> findByName(String name);
    long countByStatus(HubStatus status);
}
