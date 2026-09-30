package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.FinOpsEfficiencyRating;
import com.openshift.portal.domain.enums.FinOpsRecommendationAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinOpsNamespaceRecommendationDto {
    private UUID namespaceId;
    private String namespaceName;
    private UUID clusterId;
    private String clusterName;
    private Environment environment;
    private String teamName;
    private String costCenter;

    private BigDecimal avgCpuRequestCores;
    private BigDecimal avgCpuUsageCores;
    private BigDecimal cpuEfficiencyPercent;

    private BigDecimal avgMemoryRequestGb;
    private BigDecimal avgMemoryUsageGb;
    private BigDecimal memoryEfficiencyPercent;

    private BigDecimal pvcRequestGb;

    private BigDecimal monthlyAllocatedCost;
    private BigDecimal monthlyActualCost;
    private BigDecimal monthlyWastedCost;
    private BigDecimal overallEfficiencyPercent;

    private FinOpsEfficiencyRating rating;
    private FinOpsRecommendationAction action;

    private BigDecimal recommendedCpuRequestCores;
    private BigDecimal recommendedMemoryRequestGb;
    private BigDecimal monthlyPotentialSavings;

    private String suggestedResourceQuotaYaml;
}
