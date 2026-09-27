package com.openshift.portal.service;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.*;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.dto.LicenseAuditDto;
import com.openshift.portal.dto.NodeAgentStatusDto;
import com.openshift.portal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LicensingService {

    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeMetricsRepository;
    private final LicenseWatermarkRepository watermarkRepository;
    private final ClusterRepository clusterRepository;
    private final NodeAgentReportService nodeAgentReports;
    private final AcmProperties properties;

    /**
     * Calculates the license core count for a cluster based on Red Hat OpenShift licensing guidelines:
     * - Only Worker nodes contribute to billable license cores (Control Plane / Infra nodes excluded).
     * - Bare-metal worker nodes are accounted according to physical core/socket guidelines.
     * - Virtual machine worker nodes count assigned vCPUs.
     */
    public int calculateLicenseCores(List<NodeMetricsSnapshot> nodes, InfrastructureType infraType) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }

        int billableCores = 0;
        for (NodeMetricsSnapshot node : nodes) {
            if (node.getRole() == NodeRole.WORKER) {
                int cores = node.getCpuCores() != null ? node.getCpuCores() : 0;
                if ("BARE_METAL".equalsIgnoreCase(node.getHostType()) || infraType == InfrastructureType.BARE_METAL) {
                    // For bare metal, round to minimum subscription increments or apply bare-metal factor
                    billableCores += cores;
                } else {
                    // Virtualized vCPU core count
                    billableCores += cores;
                }
            }
        }

        double multiplier = properties.getLicensing().getDefaultCoreMultiplier();
        return (int) Math.ceil(billableCores * multiplier);
    }

    /**
     * License compliance audit over the clusters' latest snapshots. Clusters without node data are listed with the
     * reason instead of being counted as 0 cores, and keep the audit INCOMPLETE unless the known cores already breach
     * the cap.
     */
    @Transactional(readOnly = true)
    public LicenseAuditDto generateLicenseAudit() {
        List<ClusterSnapshot> latestSnapshots = snapshotRepository.findLatestSnapshotsForAllClusters();
        Map<String, NodeAgentStatusDto> agentReports = nodeAgentReports.statusesByCluster();

        int totalLicenseCores = 0;
        int workerNodesCount = 0;
        int masterNodesCount = 0;
        int bareMetalCores = 0;
        int virtualCores = 0;
        int clustersCounted = 0;

        Map<String, Integer> coresByEnvironment = new HashMap<>();
        Map<String, Integer> coresByOwnerTeam = new HashMap<>();
        Map<String, Integer> coresByInfrastructure = new HashMap<>();
        List<LicenseAuditDto.NodeDataGap> gaps = new ArrayList<>();
        Set<UUID> collectedClusters = new HashSet<>();

        for (ClusterSnapshot snapshot : latestSnapshots) {
            Cluster cluster = snapshot.getCluster();
            collectedClusters.add(cluster.getId());
            if (!snapshot.hasNodeData()) {
                gaps.add(gap(cluster, agentReports.get(cluster.getClusterName()), true));
                continue;
            }
            clustersCounted++;
            int cores = snapshot.getLicenseCoresCount() != null ? snapshot.getLicenseCoresCount() : 0;
            totalLicenseCores += cores;
            workerNodesCount += snapshot.getWorkerNodes() != null ? snapshot.getWorkerNodes() : 0;
            masterNodesCount += (snapshot.getTotalNodes() != null && snapshot.getWorkerNodes() != null)
                    ? Math.max(0, snapshot.getTotalNodes() - snapshot.getWorkerNodes()) : 0;

            String envKey = cluster.getEnvironment() != null ? cluster.getEnvironment().name() : "UNKNOWN";
            coresByEnvironment.put(envKey, coresByEnvironment.getOrDefault(envKey, 0) + cores);

            String teamKey = (cluster.getOwnerTeam() != null) ? cluster.getOwnerTeam().getName() : "Unassigned";
            coresByOwnerTeam.put(teamKey, coresByOwnerTeam.getOrDefault(teamKey, 0) + cores);

            String infraKey = cluster.getInfrastructureType() != null ? cluster.getInfrastructureType().name() : "UNKNOWN";
            coresByInfrastructure.put(infraKey, coresByInfrastructure.getOrDefault(infraKey, 0) + cores);

            if (cluster.getInfrastructureType() == InfrastructureType.BARE_METAL) {
                bareMetalCores += cores;
            } else {
                virtualCores += cores;
            }
        }
        // Registered clusters whose collections have all failed so far have no snapshot at all
        for (Cluster cluster : clusterRepository.findAll()) {
            if (!collectedClusters.contains(cluster.getId())) {
                gaps.add(gap(cluster, agentReports.get(cluster.getClusterName()), false));
            }
        }
        gaps.sort(Comparator.comparing(LicenseAuditDto.NodeDataGap::clusterName));

        int cap = properties.getLicensing().getLicensedCapCores();
        Integer recordedPeak = watermarkRepository.findPeakSince(
                LocalDate.now().minusDays(properties.getLicensing().getWatermarkPeriodDays()));
        int peak = Math.max(totalLicenseCores, recordedPeak != null ? recordedPeak : 0);
        boolean breach = peak > cap;
        LicenseAuditDto.ComplianceStatus status = breach ? LicenseAuditDto.ComplianceStatus.BREACH
                : gaps.isEmpty() ? LicenseAuditDto.ComplianceStatus.COMPLIANT
                : LicenseAuditDto.ComplianceStatus.INCOMPLETE;

        return LicenseAuditDto.builder()
                .totalLicenseCores(totalLicenseCores)
                .licensedCapCores(cap)
                .highWatermarkCores(peak)
                .complianceBreach(breach)
                .complianceStatus(status)
                .workerNodesCount(workerNodesCount)
                .masterNodesCount(masterNodesCount)
                .bareMetalCores(bareMetalCores)
                .virtualCores(virtualCores)
                .coresByEnvironment(coresByEnvironment)
                .coresByOwnerTeam(coresByOwnerTeam)
                .coresByInfrastructure(coresByInfrastructure)
                .clustersCounted(clustersCounted)
                .clustersWithoutNodeData(gaps)
                .build();
    }

    /**
     * Raises today's watermark to the fleet's current license cores when they are higher, so the high watermark is
     * the highest value any collection saw that day. Clusters without node data add nothing, so it is a lower bound.
     */
    @Transactional
    public LicenseWatermark recordDailyWatermark() {
        Integer current = snapshotRepository.sumLatestLicenseCoresCount();
        int cores = current != null ? current : 0;
        int cap = properties.getLicensing().getLicensedCapCores();
        LocalDate today = LocalDate.now();
        LicenseWatermark watermark = watermarkRepository.findByWatermarkDate(today)
                .orElseGet(() -> LicenseWatermark.builder().watermarkDate(today).peakWorkerCores(0).build());
        watermark.setPeakWorkerCores(Math.max(watermark.getPeakWorkerCores(), cores));
        watermark.setLicensedCapCores(cap);
        watermark.setComplianceBreach(watermark.getPeakWorkerCores() > cap);
        return watermarkRepository.save(watermark);
    }

    private static LicenseAuditDto.NodeDataGap gap(Cluster cluster, NodeAgentStatusDto agentReport, boolean collected) {
        LicenseAuditDto.NodeDataGapReason reason;
        if (!collected) {
            reason = LicenseAuditDto.NodeDataGapReason.NOT_COLLECTED;
        } else if (agentReport == null) {
            reason = LicenseAuditDto.NodeDataGapReason.NO_AGENT_REPORT;
        } else if (!agentReport.fresh()) {
            reason = LicenseAuditDto.NodeDataGapReason.STALE_AGENT_REPORT;
        } else {
            reason = LicenseAuditDto.NodeDataGapReason.AWAITING_COLLECTION;
        }
        return new LicenseAuditDto.NodeDataGap(cluster.getClusterName(),
                cluster.getEnvironment() != null ? cluster.getEnvironment().name() : "UNKNOWN", reason,
                agentReport != null ? agentReport.receivedAt() : null);
    }
}
