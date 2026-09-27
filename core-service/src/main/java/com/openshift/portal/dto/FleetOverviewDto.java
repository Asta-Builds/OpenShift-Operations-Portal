package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetOverviewDto {
    private long totalClusters;
    private long activeAcmHubs;
    private int totalCpuCores;
    private double allocatedCpuCores;
    private double cpuUtilizationPercent;
    private double totalMemoryGb;
    private double allocatedMemoryGb;
    private double memoryUtilizationPercent;
    private double totalStorageGb;
    private double allocatedStorageGb;
    private double storageUtilizationPercent;
    private int totalLicenseCores;
    /** Clusters left out of totalLicenseCores because their nodes are unknown; while above 0 the total is a lower bound. */
    private long clustersWithoutNodeData;
    private Map<String, Long> clustersByEnvironment;
    private Map<String, Long> clustersByInfrastructure;
}
