package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.Environment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinOpsOverviewDto {
    private LocalDate from;
    private LocalDate to;
    private Environment environment;
    private String currency;

    private BigDecimal totalMonthlyAllocatedCost;
    private BigDecimal totalMonthlyActualCost;
    private BigDecimal totalMonthlyWastedCost;
    private BigDecimal totalAnnualizedSavingsPotential;
    private BigDecimal overallFleetEfficiencyPercent;

    private int totalNamespacesAnalyzed;
    private int severeWasteNamespacesCount;
    private int overProvisionedNamespacesCount;
    private int acceptableNamespacesCount;
    private int optimalNamespacesCount;
    private int underProvisionedNamespacesCount;

    private List<FinOpsTeamBreakdownDto> teamBreakdowns;
    private Map<String, BigDecimal> costByEnvironment;
    private List<FinOpsNamespaceRecommendationDto> topWastefulNamespaces;
    private FinOpsPricingConfigDto pricing;
}
