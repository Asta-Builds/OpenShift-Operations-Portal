package com.openshift.portal.service;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.*;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.dto.LicenseAuditDto;
import com.openshift.portal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LicensingService {

    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeMetricsRepository;
    private final LicenseWatermarkRepository watermarkRepository;
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
     * Builds an enterprise license compliance audit summary across all managed clusters.
     */
    @Transactional(readOnly = true)
    public LicenseAuditDto generateLicenseAudit() {
        List<ClusterSnapshot> latestSnapshots = snapshotRepository.findLatestSnapshotsForAllClusters();

        int totalLicenseCores = 0;
        int workerNodesCount = 0;
        int masterNodesCount = 0;
        int bareMetalCores = 0;
        int virtualCores = 0;

        Map<String, Integer> coresByEnvironment = new HashMap<>();
        Map<String, Integer> coresByOwnerTeam = new HashMap<>();
        Map<String, Integer> coresByInfrastructure = new HashMap<>();

        for (ClusterSnapshot snapshot : latestSnapshots) {
            Cluster cluster = snapshot.getCluster();
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

        int cap = 500;
        int peak = Math.max(totalLicenseCores, watermarkRepository.findTopByOrderByWatermarkDateDesc()
                .map(LicenseWatermark::getPeakWorkerCores)
                .orElse(totalLicenseCores));
        boolean breach = peak > cap;

        return LicenseAuditDto.builder()
                .totalLicenseCores(totalLicenseCores)
                .licensedCapCores(cap)
                .highWatermarkCores(peak)
                .complianceBreach(breach)
                .workerNodesCount(workerNodesCount)
                .masterNodesCount(masterNodesCount)
                .bareMetalCores(bareMetalCores)
                .virtualCores(virtualCores)
                .coresByEnvironment(coresByEnvironment)
                .coresByOwnerTeam(coresByOwnerTeam)
                .coresByInfrastructure(coresByInfrastructure)
                .build();
    }
}
