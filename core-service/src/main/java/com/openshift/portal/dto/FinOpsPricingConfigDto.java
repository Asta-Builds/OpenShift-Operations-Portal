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
public class FinOpsPricingConfigDto {
    @Builder.Default
    private BigDecimal cpuHourlyRate = new BigDecimal("0.045"); // $0.045 per vCPU-hour

    @Builder.Default
    private BigDecimal memoryHourlyRate = new BigDecimal("0.006"); // $0.006 per GB RAM-hour

    @Builder.Default
    private BigDecimal storageMonthlyRate = new BigDecimal("0.10"); // $0.10 per GB-month

    @Builder.Default
    private String currency = "USD";
}
