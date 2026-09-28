package com.openshift.portal.service;

import com.opencsv.CSVWriter;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.dto.AttributionReportDto;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

    private final ClusterRepository clusterRepository;
    private final ClusterSnapshotRepository snapshotRepository;
    private final AttributionService attributionService;

    @Transactional(readOnly = true)
    public byte[] generateCsvReport(ReportType type) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter writer = new CSVWriter(osw)) {

            List<ClusterSnapshot> latestSnapshots = snapshotRepository.findLatestSnapshotsForAllClusters();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            switch (type) {
                case LICENSE_AUDIT -> {
                    // Without node data the node and core counts are unknown: left empty, never written as 0
                    writer.writeNext(new String[]{"Cluster Name", "Environment", "Owner Team", "Infrastructure", "Worker Nodes", "License Cores", "Collected At", "Node Data"});
                    for (ClusterSnapshot snap : latestSnapshots) {
                        Cluster c = snap.getCluster();
                        boolean known = snap.hasNodeData();
                        writer.writeNext(new String[]{
                                c.getClusterName(),
                                c.getEnvironment() != null ? c.getEnvironment().name() : "N/A",
                                c.getOwnerTeam() != null ? c.getOwnerTeam().getName() : "Unassigned",
                                c.getInfrastructureType() != null ? c.getInfrastructureType().name() : "N/A",
                                known ? String.valueOf(snap.getWorkerNodes()) : "",
                                known ? String.valueOf(snap.getLicenseCoresCount()) : "",
                                snap.getSnapshotTimestamp().format(formatter),
                                known ? "YES" : "MISSING"
                        });
                    }
                }
                case COST_ATTRIBUTION -> {
                    // Namespace-level attribution over the last 30 days; the cluster owner is never used
                    AttributionReportDto report = attributionService.attribute(
                            LocalDate.now().minusDays(29), LocalDate.now(), null);
                    writer.writeNext(AttributionTable.HEADER);
                    AttributionTable.rows(report).forEach(writer::writeNext);
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
                                snap.getAllocatedCpuCores().toPlainString(),
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
}
