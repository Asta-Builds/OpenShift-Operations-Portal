package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.FinOpsEfficiencyRating;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfSimulationRequestDto {
    /** Adoption rate of rightsizing recommendations (0 to 100%). Default: 0 */
    @Builder.Default
    private int rightsizingAdoptionPercent = 0;

    /** Tiers to apply rightsizing to (e.g. SEVERE_WASTE, OVER_PROVISIONED). If empty or null, applies to all tiers with waste */
    private List<FinOpsEfficiencyRating> targetEfficiencyRatings;

    /** New hypothetical workloads to be onboarded */
    private List<WhatIfWorkloadDto> additionalWorkloads;

    /** Clusters to be decommissioned and drained to another target cluster */
    private List<WhatIfDecommissionDto> clusterDecommissions;

    /** Optional growth factor for existing workloads across the entire fleet (-50% to +100%). Default: 0 */
    @Builder.Default
    private int fleetGrowthPercent = 0;
}
