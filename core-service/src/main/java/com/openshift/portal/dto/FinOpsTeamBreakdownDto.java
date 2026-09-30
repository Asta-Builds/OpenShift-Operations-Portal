package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinOpsTeamBreakdownDto {
    private String teamName;
    private String costCenter;
    private int namespaceCount;
    private BigDecimal monthlyAllocatedCost;
    private BigDecimal monthlyActualCost;
    private BigDecimal monthlyWastedCost;
    private BigDecimal monthlyPotentialSavings;
    private BigDecimal costSharePercent;
    private BigDecimal efficiencyScorePercent;
}
