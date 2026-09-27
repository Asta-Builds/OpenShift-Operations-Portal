package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.SavedReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedReportRepository extends JpaRepository<SavedReport, UUID> {
    List<SavedReport> findByUserId(String userId);
}
