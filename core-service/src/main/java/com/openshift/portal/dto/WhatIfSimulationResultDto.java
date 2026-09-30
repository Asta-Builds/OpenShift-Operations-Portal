package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfSimulationResultDto {
    private BigDecimal baselineMonthlySpend;
    private BigDecimal simulatedMonthlySpend;
    private BigDecimal monthlySavingsDelta; // Positive means savings, negative means added cost
    private BigDecimal annualizedSavingsDelta;

    private BigDecimal rightsizingMonthlySavings;
    private BigDecimal hardwareAndLicenseMonthlySavings;
    private BigDecimal newWorkloadsMonthlyCost;

    private double totalFreedCpuCores;
    private double totalFreedMemoryGb;
    private double totalFreedStorageGb;

    private double baselineFleetCpuAllocPercent;
    private double simulatedFleetCpuAllocPercent;
    private double baselineFleetMemoryAllocPercent;
    private double simulatedFleetMemoryAllocPercent;

    private int totalWorkerNodesDelta;
    private int totalLicenseCoresDelta;

    private List<WhatIfClusterImpactDto> clusterImpacts;
    private List<String> globalWarnings;
    private List<String> strategicRecommendations;
}
