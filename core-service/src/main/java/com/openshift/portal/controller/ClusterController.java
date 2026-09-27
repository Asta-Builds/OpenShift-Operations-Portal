package com.openshift.portal.controller;

import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.dto.ClusterDetailDto;
import com.openshift.portal.dto.ClusterSummaryDto;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.exception.ResourceNotFoundException;
import com.openshift.portal.repository.*;
import com.openshift.portal.service.AcmCollectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterRepository clusterRepository;
    private final ClusterSnapshotRepository snapshotRepository;
    private final NodeMetricsSnapshotRepository nodeMetricsRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceSnapshotRepository namespaceSnapshotRepository;
    private final AcmCollectorService collectorService;

    @GetMapping
    public ResponseEntity<List<ClusterSummaryDto>> getAllClusters() {
        List<Cluster> clusters = clusterRepository.findAll();
        List<ClusterSummaryDto> result = new ArrayList<>();

        for (Cluster c : clusters) {
            Optional<ClusterSnapshot> latest = snapshotRepository.findTopByClusterOrderBySnapshotTimestampDesc(c);

            ClusterSummaryDto dto = ClusterSummaryDto.builder()
                    .id(c.getId())
                    .clusterName(c.getClusterName())
                    .acmHubName(c.getAcmHub() != null ? c.getAcmHub().getName() : "Unassigned")
                    .environment(c.getEnvironment())
                    .ownerTeamName(c.getOwnerTeam() != null ? c.getOwnerTeam().getName() : "Unassigned")
                    .infrastructureType(c.getInfrastructureType())
                    .openshiftVersion(c.getOpenshiftVersion())
                    .status(c.getStatus())
                    .totalCores(latest.map(ClusterSnapshot::getTotalCpuCores).orElse(0))
                    .allocatedCores(latest.map(ClusterSnapshot::getAllocatedCpuCores).orElse(0))
                    .totalMemoryGb(latest.map(s -> s.getTotalMemoryGb().doubleValue()).orElse(0.0))
                    .allocatedMemoryGb(latest.map(s -> s.getAllocatedMemoryGb().doubleValue()).orElse(0.0))
                    .licenseCores(latest.map(ClusterSnapshot::getLicenseCoresCount).orElse(0))
                    .lastSnapshotTime(latest.map(ClusterSnapshot::getSnapshotTimestamp).orElse(null))
                    .build();

            result.add(dto);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClusterDetailDto> getClusterById(@PathVariable UUID id) {
        Cluster c = clusterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with ID: " + id));

        Optional<ClusterSnapshot> latestOpt = snapshotRepository.findTopByClusterOrderBySnapshotTimestampDesc(c);
        List<ClusterSnapshot> recentSnapshots = snapshotRepository.findByClusterOrderBySnapshotTimestampDesc(c, PageRequest.of(0, 10));

        List<ClusterDetailDto.SnapshotDto> recentSnapshotDtos = recentSnapshots.stream()
                .map(this::mapSnapshotDto)
                .toList();

        ClusterDetailDto.SnapshotDto latestDto = latestOpt.map(this::mapSnapshotDto).orElse(null);

        List<NodeMetricsSnapshot> nodes = latestOpt
                .map(s -> nodeMetricsRepository.findBySnapshotId(s.getId()))
                .orElse(List.of());

        List<ClusterDetailDto.NodeMetricDto> nodeDtos = nodes.stream()
                .map(n -> ClusterDetailDto.NodeMetricDto.builder()
                        .id(n.getId())
                        .nodeName(n.getNodeName())
                        .role(n.getRole())
                        .hostType(n.getHostType())
                        .cpuCores(n.getCpuCores())
                        .memoryGb(n.getMemoryGb() != null ? n.getMemoryGb().doubleValue() : 0.0)
                        .underlyingHostId(n.getUnderlyingHostId())
                        .providerId(n.getProviderId())
                        .hypervisorHost(n.getHypervisorHost())
                        .sockets(n.getSockets())
                        .build())
                .toList();

        List<ClusterDetailDto.NamespaceSummaryDto> namespaceDtos = namespaceRepository.findByClusterId(c.getId()).stream()
                .map(ns -> {
                    var latestNsSnap = namespaceSnapshotRepository.findTopByNamespaceIdOrderBySnapshotTimestampDesc(ns.getId());
                    return ClusterDetailDto.NamespaceSummaryDto.builder()
                            .id(ns.getId())
                            .namespaceName(ns.getNamespaceName())
                            .ownerTeamName(ns.getOwnerTeam() != null ? ns.getOwnerTeam().getName() : "Unassigned")
                            .costCenter(ns.getCostCenter())
                            .cpuRequestCores(latestNsSnap.map(s -> s.getCpuRequestCores().doubleValue()).orElse(0.0))
                            .memoryRequestGb(latestNsSnap.map(s -> s.getMemoryRequestGb().doubleValue()).orElse(0.0))
                            .build();
                })
                .toList();

        ClusterDetailDto detail = ClusterDetailDto.builder()
                .id(c.getId())
                .clusterName(c.getClusterName())
                .acmHubName(c.getAcmHub() != null ? c.getAcmHub().getName() : "Unassigned")
                .environment(c.getEnvironment())
                .ownerTeamName(c.getOwnerTeam() != null ? c.getOwnerTeam().getName() : "Unassigned")
                .costCenter(c.getOwnerTeam() != null ? c.getOwnerTeam().getCostCenter() : "N/A")
                .infrastructureType(c.getInfrastructureType())
                .openshiftVersion(c.getOpenshiftVersion())
                .region(c.getRegion())
                .status(c.getStatus())
                .latestSnapshot(latestDto)
                .nodeMetrics(nodeDtos)
                .namespaces(namespaceDtos)
                .recentSnapshots(recentSnapshotDtos)
                .build();

        return ResponseEntity.ok(detail);
    }

    @PostMapping("/collect")
    public ResponseEntity<SnapshotTriggerResultDto> triggerCollection() {
        SnapshotTriggerResultDto result = collectorService.triggerCollection();
        return ResponseEntity.ok(result);
    }

    private ClusterDetailDto.SnapshotDto mapSnapshotDto(ClusterSnapshot s) {
        return ClusterDetailDto.SnapshotDto.builder()
                .id(s.getId())
                .snapshotTimestamp(s.getSnapshotTimestamp())
                .totalCpuCores(s.getTotalCpuCores())
                .allocatedCpuCores(s.getAllocatedCpuCores())
                .totalMemoryGb(s.getTotalMemoryGb() != null ? s.getTotalMemoryGb().doubleValue() : 0.0)
                .allocatedMemoryGb(s.getAllocatedMemoryGb() != null ? s.getAllocatedMemoryGb().doubleValue() : 0.0)
                .totalStorageGb(s.getTotalStorageGb() != null ? s.getTotalStorageGb().doubleValue() : 0.0)
                .allocatedStorageGb(s.getAllocatedStorageGb() != null ? s.getAllocatedStorageGb().doubleValue() : 0.0)
                .licenseCoresCount(s.getLicenseCoresCount())
                .totalNodes(s.getTotalNodes())
                .workerNodes(s.getWorkerNodes())
                .rawPayload(s.getRawPayload())
                .build();
    }
}
