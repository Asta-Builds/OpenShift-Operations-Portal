package com.openshift.portal.repository;

import com.openshift.portal.domain.entity.NodeAgentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NodeAgentReportRepository extends JpaRepository<NodeAgentReport, String> {
}
