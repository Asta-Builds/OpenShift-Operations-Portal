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
    private int allocatedCpuCores;
    private double cpuUtilizationPercent;
    private double totalMemoryGb;
    private double allocatedMemoryGb;
    private double memoryUtilizationPercent;
    private double totalStorageGb;
    private double allocatedStorageGb;
    private double storageUtilizationPercent;
    private int totalLicenseCores;
    private Map<String, Long> clustersByEnvironment;
    private Map<String, Long> clustersByInfrastructure;
}
