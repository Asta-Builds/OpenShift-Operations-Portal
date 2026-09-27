package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastingProjectionDto {
    private int horizonDays;
    /** True when fewer than two daily data points exist; projection and runway fields are then unset. */
    private boolean insufficientData;
    /** Number of daily points the regression was fitted on. */
    private int dataPoints;
    private double currentCores;
    private double projectedCores;
    private double estimatedGrowthPercent;
    private double currentMemoryGb;
    private double projectedMemoryGb;
    private double dailyGrowthRateCores;
    /** Coefficient of determination of the linear fit; null when undefined (fewer than three points or a flat series). */
    private Double coresRSquared;
    private Double memoryRSquared;
    /** Days until allocation reaches capacity: 0 = already reached, null = no projected exhaustion or unknown capacity. */
    private Integer runwayDaysCores;
    private Integer runwayDaysMemory;
    private LocalDate exhaustionDateCores;
    private LocalDate exhaustionDateMemory;
    private int totalCapacityCores;
    private double totalCapacityMemoryGb;
    private boolean capacityAlert;
    private List<TrendPointDto> historicalPoints;
    private List<TrendPointDto> projectedPoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPointDto {
        private LocalDate date;
        private double cores;
        private double memoryGb;
    }
}
