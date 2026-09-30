package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.HeadroomStatus;
import com.openshift.portal.domain.enums.InfrastructureType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfClusterImpactDto {
    private UUID clusterId;
    private String clusterName;
    private Environment environment;
    private InfrastructureType infrastructureType;
    private boolean decommissioned;

    private int totalCores;
    private double baselineAllocatedCores;
    private double simulatedAllocatedCores;
    private double baselineCpuAllocPercent;
    private double simulatedCpuAllocPercent;

    private double totalMemoryGb;
    private double baselineAllocatedMemoryGb;
    private double simulatedAllocatedMemoryGb;
    private double baselineMemoryAllocPercent;
    private double simulatedMemoryAllocPercent;

    private double totalStorageGb;
    private double baselineAllocatedStorageGb;
    private double simulatedAllocatedStorageGb;

    private HeadroomStatus headroomStatus;
    private String statusDescription;

    /** Suggested node scaling action: e.g. -1 (remove 1 worker node), +2 (add 2 worker nodes), 0 (stable) */
    private int suggestedWorkerNodeDelta;
    private int estimatedLicenseCoreDelta;
    private BigDecimal monthlyCostDelta;

    private List<String> warnings;
}
