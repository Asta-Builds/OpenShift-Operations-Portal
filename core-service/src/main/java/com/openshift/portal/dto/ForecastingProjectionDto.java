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
    private int currentCores;
    private int projectedCores;
    private double estimatedGrowthPercent;
    private double currentMemoryGb;
    private double projectedMemoryGb;
    private double dailyGrowthRateCores;
    private double confidenceScore;
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
        private int cores;
        private double memoryGb;
    }
}
