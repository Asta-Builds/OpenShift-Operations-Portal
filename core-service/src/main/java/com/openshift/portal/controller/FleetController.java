package com.openshift.portal.controller;

import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.dto.FleetOverviewDto;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fleet")
@RequiredArgsConstructor
public class FleetController {

    private final ClusterRepository clusterRepository;
    private final AcmHubRepository acmHubRepository;
    private final ClusterSnapshotRepository snapshotRepository;

    @GetMapping("/overview")
    public ResponseEntity<FleetOverviewDto> getFleetOverview() {
        long totalClusters = clusterRepository.count();
        long activeHubs = acmHubRepository.count();

        List<ClusterSnapshot> latestSnapshots = snapshotRepository.findLatestSnapshotsForAllClusters();

        int totalCores = 0;
        int allocatedCores = 0;
        double totalMem = 0.0;
        double allocatedMem = 0.0;
        double totalStorage = 0.0;
        double allocatedStorage = 0.0;
        int totalLicenseCores = 0;

        for (ClusterSnapshot snap : latestSnapshots) {
            totalCores += snap.getTotalCpuCores() != null ? snap.getTotalCpuCores() : 0;
            allocatedCores += snap.getAllocatedCpuCores() != null ? snap.getAllocatedCpuCores() : 0;
            totalMem += snap.getTotalMemoryGb() != null ? snap.getTotalMemoryGb().doubleValue() : 0.0;
            allocatedMem += snap.getAllocatedMemoryGb() != null ? snap.getAllocatedMemoryGb().doubleValue() : 0.0;
            totalStorage += snap.getTotalStorageGb() != null ? snap.getTotalStorageGb().doubleValue() : 0.0;
            allocatedStorage += snap.getAllocatedStorageGb() != null ? snap.getAllocatedStorageGb().doubleValue() : 0.0;
            totalLicenseCores += snap.getLicenseCoresCount() != null ? snap.getLicenseCoresCount() : 0;
        }

        double cpuUtil = (totalCores > 0) ? Math.round(((double) allocatedCores / totalCores) * 10000.0) / 100.0 : 0.0;
        double memUtil = (totalMem > 0) ? Math.round((allocatedMem / totalMem) * 10000.0) / 100.0 : 0.0;
        double storageUtil = (totalStorage > 0) ? Math.round((allocatedStorage / totalStorage) * 10000.0) / 100.0 : 0.0;

        Map<String, Long> envMap = new HashMap<>();
        for (Object[] row : clusterRepository.countByEnvironmentGroup()) {
            envMap.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> infraMap = new HashMap<>();
        for (Object[] row : clusterRepository.countByInfrastructureTypeGroup()) {
            infraMap.put(row[0].toString(), (Long) row[1]);
        }

        FleetOverviewDto overview = FleetOverviewDto.builder()
                .totalClusters(totalClusters)
                .activeAcmHubs(activeHubs)
                .totalCpuCores(totalCores)
                .allocatedCpuCores(allocatedCores)
                .cpuUtilizationPercent(cpuUtil)
                .totalMemoryGb(Math.round(totalMem * 100.0) / 100.0)
                .allocatedMemoryGb(Math.round(allocatedMem * 100.0) / 100.0)
                .memoryUtilizationPercent(memUtil)
                .totalStorageGb(Math.round(totalStorage * 100.0) / 100.0)
                .allocatedStorageGb(Math.round(allocatedStorage * 100.0) / 100.0)
                .storageUtilizationPercent(storageUtil)
                .totalLicenseCores(totalLicenseCores)
                .clustersByEnvironment(envMap)
                .clustersByInfrastructure(infraMap)
                .build();

        return ResponseEntity.ok(overview);
    }
}
