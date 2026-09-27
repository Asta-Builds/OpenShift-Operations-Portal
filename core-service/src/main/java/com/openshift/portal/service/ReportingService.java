package com.openshift.portal.service;

import com.opencsv.CSVWriter;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.ReportDefinition;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.ReportDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

    private final ClusterRepository clusterRepository;
    private final ClusterSnapshotRepository snapshotRepository;
    private final ReportDefinitionRepository reportRepository;

    @Transactional(readOnly = true)
    public byte[] generateCsvReport(ReportType type) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter writer = new CSVWriter(osw)) {

            List<ClusterSnapshot> latestSnapshots = snapshotRepository.findLatestSnapshotsForAllClusters();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            switch (type) {
                case LICENSE_AUDIT -> {
                    writer.writeNext(new String[]{"Cluster Name", "Environment", "Owner Team", "Infrastructure", "Worker Nodes", "License Cores", "Collected At"});
                    for (ClusterSnapshot snap : latestSnapshots) {
                        Cluster c = snap.getCluster();
                        writer.writeNext(new String[]{
                                c.getClusterName(),
                                c.getEnvironment() != null ? c.getEnvironment().name() : "N/A",
                                c.getOwnerTeam() != null ? c.getOwnerTeam().getName() : "Unassigned",
                                c.getInfrastructureType() != null ? c.getInfrastructureType().name() : "N/A",
                                String.valueOf(snap.getWorkerNodes()),
                                String.valueOf(snap.getLicenseCoresCount()),
                                snap.getSnapshotTimestamp().format(formatter)
                        });
                    }
                }
                case COST_ATTRIBUTION -> {
                    writer.writeNext(new String[]{"Owner Team", "Cost Center", "Cluster Name", "Environment", "Total Cores", "Allocated Cores", "Allocated Mem (GB)"});
                    for (ClusterSnapshot snap : latestSnapshots) {
                        Cluster c = snap.getCluster();
                        writer.writeNext(new String[]{
                                c.getOwnerTeam() != null ? c.getOwnerTeam().getName() : "Unassigned",
                                c.getOwnerTeam() != null ? c.getOwnerTeam().getCostCenter() : "N/A",
                                c.getClusterName(),
                                c.getEnvironment() != null ? c.getEnvironment().name() : "N/A",
                                String.valueOf(snap.getTotalCpuCores()),
                                String.valueOf(snap.getAllocatedCpuCores()),
                                snap.getAllocatedMemoryGb() != null ? snap.getAllocatedMemoryGb().toString() : "0.00"
                        });
                    }
                }
                default -> { // FLEET_CAPACITY
                    writer.writeNext(new String[]{"Cluster Name", "Environment", "OpenShift Version", "Total Cores", "Allocated Cores", "Total Mem (GB)", "Allocated Mem (GB)", "Total Nodes", "Collected At"});
                    for (ClusterSnapshot snap : latestSnapshots) {
                        Cluster c = snap.getCluster();
                        writer.writeNext(new String[]{
                                c.getClusterName(),
                                c.getEnvironment() != null ? c.getEnvironment().name() : "N/A",
                                c.getOpenshiftVersion() != null ? c.getOpenshiftVersion() : "N/A",
                                String.valueOf(snap.getTotalCpuCores()),
                                String.valueOf(snap.getAllocatedCpuCores()),
                                snap.getTotalMemoryGb() != null ? snap.getTotalMemoryGb().toString() : "0.00",
                                snap.getAllocatedMemoryGb() != null ? snap.getAllocatedMemoryGb().toString() : "0.00",
                                String.valueOf(snap.getTotalNodes()),
                                snap.getSnapshotTimestamp().format(formatter)
                        });
                    }
                }
            }

            osw.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating CSV report for type {}: {}", type, e.getMessage(), e);
            throw new RuntimeException("Report generation error: " + e.getMessage(), e);
        }
    }

    @Transactional
    public ReportDefinition createReportSchedule(String title, ReportType type, String cronSchedule, String recipients) {
        ReportDefinition report = ReportDefinition.builder()
                .title(title)
                .reportType(type)
                .cronSchedule(cronSchedule)
                .recipients(recipients)
                .isEnabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        return reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<ReportDefinition> listReports() {
        return reportRepository.findAll();
    }
}
