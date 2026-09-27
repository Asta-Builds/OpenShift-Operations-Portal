package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.ReportDefinition;
import com.openshift.portal.domain.enums.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {
    List<ReportDefinition> findByIsEnabledTrue();
    List<ReportDefinition> findByReportType(ReportType reportType);
}
