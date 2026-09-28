package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    boolean existsByDedupeKey(String dedupeKey);

    Optional<Notification> findTopByReportIdOrderByCreatedAtDescIdDesc(UUID reportId);
}
