package com.openshift.portal.service;

import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.dto.ForecastingProjectionDto;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastingService {

    private final ClusterSnapshotRepository snapshotRepository;

    /**
     * Estimates future resource growth (cores and memory) over rolling 30, 60, or 90-day horizons
     * using linear regression over historical fleet snapshots.
     */
    @Transactional(readOnly = true)
    public ForecastingProjectionDto generateProjection(int horizonDays, UUID clusterId) {
        if (horizonDays <= 0) {
            horizonDays = 30;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lookbackStart = now.minusDays(horizonDays);

        List<ClusterSnapshot> historicalSnapshots;
        if (clusterId != null) {
            historicalSnapshots = snapshotRepository.findByClusterIdAndSnapshotTimestampBetweenOrderBySnapshotTimestampAsc(
                    clusterId, lookbackStart, now);
        } else {
            historicalSnapshots = snapshotRepository.findBySnapshotTimestampBetweenOrderBySnapshotTimestampAsc(
                    lookbackStart, now);
        }

        // Keep only the latest snapshot per cluster per day. Collection runs many times a day, so summing
        // every snapshot would multiply each daily total by the number of collection cycles.
        Map<LocalDate, Map<UUID, ClusterSnapshot>> latestPerClusterByDay = new TreeMap<>();
        for (ClusterSnapshot snap : historicalSnapshots) {
            latestPerClusterByDay
                    .computeIfAbsent(snap.getSnapshotTimestamp().toLocalDate(), day -> new HashMap<>())
                    .put(snap.getCluster().getId(), snap); // ascending order, so later snapshots win
        }

        // Daily totals across clusters; a cluster without a snapshot on a given day keeps its last known values.
        Map<LocalDate, DailyAggregate> dailyData = new TreeMap<>();
        Map<UUID, ClusterSnapshot> lastKnownPerCluster = new HashMap<>();
        for (Map.Entry<LocalDate, Map<UUID, ClusterSnapshot>> day : latestPerClusterByDay.entrySet()) {
            lastKnownPerCluster.putAll(day.getValue());
            DailyAggregate agg = new DailyAggregate();
            for (ClusterSnapshot snap : lastKnownPerCluster.values()) {
                agg.cores += (snap.getAllocatedCpuCores() != null && snap.getAllocatedCpuCores().signum() > 0)
                        ? snap.getAllocatedCpuCores().doubleValue()
                        : (snap.getTotalCpuCores() != null ? snap.getTotalCpuCores() : 0);
                agg.memoryGb += (snap.getAllocatedMemoryGb() != null && snap.getAllocatedMemoryGb().compareTo(BigDecimal.ZERO) > 0)
                        ? snap.getAllocatedMemoryGb().doubleValue()
                        : (snap.getTotalMemoryGb() != null ? snap.getTotalMemoryGb().doubleValue() : 0.0);
            }
            dailyData.put(day.getKey(), agg);
        }

        // Capacity of the clusters in scope, from each one's latest snapshot in the window
        int totalCapCores = lastKnownPerCluster.values().stream()
                .mapToInt(s -> s.getTotalCpuCores() != null ? s.getTotalCpuCores() : 0)
                .sum();
        double totalCapMem = lastKnownPerCluster.values().stream()
                .mapToDouble(s -> s.getTotalMemoryGb() != null ? s.getTotalMemoryGb().doubleValue() : 0.0)
                .sum();

        List<ForecastingProjectionDto.TrendPointDto> historyPoints = new ArrayList<>();
        List<LocalDate> sortedDates = new ArrayList<>(dailyData.keySet());

        List<Double> xValues = new ArrayList<>();
        List<Double> yCoresValues = new ArrayList<>();
        List<Double> yMemoryValues = new ArrayList<>();

        for (LocalDate date : sortedDates) {
            DailyAggregate agg = dailyData.get(date);
            double dayOffset = (double) ChronoUnit.DAYS.between(sortedDates.get(0), date);
            xValues.add(dayOffset);
            yCoresValues.add(agg.cores);
            yMemoryValues.add(agg.memoryGb);

            historyPoints.add(ForecastingProjectionDto.TrendPointDto.builder()
                    .date(date)
                    .cores(round2(agg.cores))
                    .memoryGb(Math.round(agg.memoryGb * 100.0) / 100.0)
                    .build());
        }

        DailyAggregate lastAgg = sortedDates.isEmpty()
                ? new DailyAggregate()
                : dailyData.get(sortedDates.get(sortedDates.size() - 1));
        double currentCores = round2(lastAgg.cores);
        double currentMemory = lastAgg.memoryGb;

        // A trend needs at least two days; never invent history to fill the gap
        if (sortedDates.size() < 2) {
            return ForecastingProjectionDto.builder()
                    .horizonDays(horizonDays)
                    .insufficientData(true)
                    .dataPoints(sortedDates.size())
                    .currentCores(currentCores)
                    .currentMemoryGb(Math.round(currentMemory * 100.0) / 100.0)
                    .totalCapacityCores(totalCapCores)
                    .totalCapacityMemoryGb(Math.round(totalCapMem * 100.0) / 100.0)
                    .historicalPoints(historyPoints)
                    .projectedPoints(List.of())
                    .build();
        }

        // Linear regression: y = slope * x + intercept
        double[] coreReg = calculateLinearRegression(xValues, yCoresValues);
        double[] memReg = calculateLinearRegression(xValues, yMemoryValues);

        double coreSlope = Math.max(0.0, coreReg[0]); // Resources typically grow or remain steady
        double memSlope = Math.max(0.0, memReg[0]);

        double projectedCoresRaw = currentCores + (coreSlope * horizonDays);
        double projectedCores = round2(projectedCoresRaw);
        double projectedMemory = Math.round((currentMemory + (memSlope * horizonDays)) * 100.0) / 100.0;

        double growthPercent = (currentCores > 0)
                ? Math.round(((projectedCores - currentCores) / currentCores) * 10000.0) / 100.0
                : 0.0;

        // Generate projected points at future weekly intervals
        List<ForecastingProjectionDto.TrendPointDto> projectedPoints = new ArrayList<>();
        LocalDate currentDate = sortedDates.get(sortedDates.size() - 1);
        int stepDays = Math.max(1, horizonDays / 5);

        for (int i = stepDays; i <= horizonDays; i += stepDays) {
            LocalDate futureDate = currentDate.plusDays(i);
            double futureCores = round2(currentCores + (coreSlope * i));
            double futureMem = Math.round((currentMemory + (memSlope * i)) * 100.0) / 100.0;

            projectedPoints.add(ForecastingProjectionDto.TrendPointDto.builder()
                    .date(futureDate)
                    .cores(futureCores)
                    .memoryGb(futureMem)
                    .build());
        }

        // Calculate capacity runway and exhaustion dates
        Integer runwayCores = runwayDays(currentCores, totalCapCores, coreSlope);
        LocalDate exhaustionCores = runwayCores != null ? currentDate.plusDays(runwayCores) : null;

        Integer runwayMem = runwayDays(currentMemory, totalCapMem, memSlope);
        LocalDate exhaustionMem = runwayMem != null ? currentDate.plusDays(runwayMem) : null;

        boolean alert = (runwayCores != null && runwayCores <= 90) || (runwayMem != null && runwayMem <= 90);

        return ForecastingProjectionDto.builder()
                .horizonDays(horizonDays)
                .dataPoints(sortedDates.size())
                .currentCores(currentCores)
                .projectedCores(projectedCores)
                .estimatedGrowthPercent(growthPercent)
                .currentMemoryGb(Math.round(currentMemory * 100.0) / 100.0)
                .projectedMemoryGb(projectedMemory)
                .dailyGrowthRateCores(Math.round(coreSlope * 100.0) / 100.0)
                .coresRSquared(calculateRSquared(xValues, yCoresValues, coreReg))
                .memoryRSquared(calculateRSquared(xValues, yMemoryValues, memReg))
                .totalCapacityCores(totalCapCores)
                .totalCapacityMemoryGb(Math.round(totalCapMem * 100.0) / 100.0)
                .runwayDaysCores(runwayCores)
                .runwayDaysMemory(runwayMem)
                .exhaustionDateCores(exhaustionCores)
                .exhaustionDateMemory(exhaustionMem)
                .capacityAlert(alert)
                .historicalPoints(historyPoints)
                .projectedPoints(projectedPoints)
                .build();
    }

    /**
     * Days until demand reaches capacity: 0 when it already has, null when capacity is unknown or demand isn't growing.
     */
    private Integer runwayDays(double current, double capacity, double dailyGrowth) {
        if (capacity <= 0) {
            return null;
        }
        if (current >= capacity) {
            return 0;
        }
        if (dailyGrowth <= 0) {
            return null;
        }
        return (int) Math.ceil((capacity - current) / dailyGrowth);
    }

    /**
     * R² of the fitted line; null when undefined (two points always fit exactly, a flat series has no variance).
     */
    private Double calculateRSquared(List<Double> x, List<Double> y, double[] regression) {
        if (x.size() < 3) {
            return null;
        }
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double ssResidual = 0.0;
        double ssTotal = 0.0;
        for (int i = 0; i < x.size(); i++) {
            double predicted = (regression[0] * x.get(i)) + regression[1];
            ssResidual += Math.pow(y.get(i) - predicted, 2);
            ssTotal += Math.pow(y.get(i) - meanY, 2);
        }
        if (ssTotal < 1e-9) {
            return null;
        }
        return Math.round((1.0 - (ssResidual / ssTotal)) * 1000.0) / 1000.0;
    }

    private double[] calculateLinearRegression(List<Double> x, List<Double> y) {
        int n = x.size();
        if (n == 0) return new double[]{0.0, 0.0};

        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumX2 = 0.0;

        for (int i = 0; i < n; i++) {
            sumX += x.get(i);
            sumY += y.get(i);
            sumXY += x.get(i) * y.get(i);
            sumX2 += x.get(i) * x.get(i);
        }

        double denominator = (n * sumX2) - (sumX * sumX);
        if (Math.abs(denominator) < 1e-6) {
            return new double[]{0.0, sumY / n};
        }

        double slope = ((n * sumXY) - (sumX * sumY)) / denominator;
        double intercept = (sumY - (slope * sumX)) / n;
        return new double[]{slope, intercept};
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class DailyAggregate {
        double cores = 0.0;
        double memoryGb = 0.0;
    }
}
