package com.openshift.portal.service;

import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.dto.ForecastingProjectionDto;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastingServiceTest {

    @Mock
    private ClusterSnapshotRepository snapshotRepository;

    private ForecastingService forecastingService;

    @BeforeEach
    void setUp() {
        forecastingService = new ForecastingService(snapshotRepository);
    }

    @Test
    void generateProjection_withHistoricalData_calculatesGrowth() {
        Cluster cluster = Cluster.builder().id(UUID.randomUUID()).build();
        LocalDateTime now = LocalDateTime.now();

        // 3 snapshots over 20 days: 100 cores -> 110 cores -> 120 cores
        ClusterSnapshot s1 = ClusterSnapshot.builder()
                .cluster(cluster)
                .snapshotTimestamp(now.minusDays(20))
                .allocatedCpuCores(100)
                .allocatedMemoryGb(BigDecimal.valueOf(200.0))
                .build();

        ClusterSnapshot s2 = ClusterSnapshot.builder()
                .cluster(cluster)
                .snapshotTimestamp(now.minusDays(10))
                .allocatedCpuCores(110)
                .allocatedMemoryGb(BigDecimal.valueOf(220.0))
                .build();

        ClusterSnapshot s3 = ClusterSnapshot.builder()
                .cluster(cluster)
                .snapshotTimestamp(now)
                .allocatedCpuCores(120)
                .allocatedMemoryGb(BigDecimal.valueOf(240.0))
                .build();

        when(snapshotRepository.findBySnapshotTimestampBetweenOrderBySnapshotTimestampAsc(any(), any()))
                .thenReturn(List.of(s1, s2, s3));

        ForecastingProjectionDto projection = forecastingService.generateProjection(30, null);

        assertThat(projection.getHorizonDays()).isEqualTo(30);
        assertThat(projection.isInsufficientData()).isFalse();
        assertThat(projection.getDataPoints()).isEqualTo(3);
        assertThat(projection.getCurrentCores()).isEqualTo(120);
        // Slope is 1.0 core per day. At +30 days, projected is 120 + 30 = 150 cores.
        assertThat(projection.getProjectedCores()).isEqualTo(150);
        assertThat(projection.getEstimatedGrowthPercent()).isEqualTo(25.0); // (150-120)/120 = 25%
        assertThat(projection.getDailyGrowthRateCores()).isEqualTo(1.0);
        assertThat(projection.getCoresRSquared()).isEqualTo(1.0); // perfectly linear history
        assertThat(projection.getHistoricalPoints()).hasSize(3);
        assertThat(projection.getProjectedPoints()).isNotEmpty();
        // No capacity was reported, so no runway is invented
        assertThat(projection.getRunwayDaysCores()).isNull();
    }

    @Test
    void generateProjection_countsOnlyTheLatestSnapshotPerClusterPerDay() {
        Cluster clusterA = Cluster.builder().id(UUID.randomUUID()).build();
        Cluster clusterB = Cluster.builder().id(UUID.randomUUID()).build();
        LocalDateTime dayOne = LocalDateTime.of(2026, 9, 10, 9, 0);
        LocalDateTime dayTwo = LocalDateTime.of(2026, 9, 20, 9, 0);

        when(snapshotRepository.findBySnapshotTimestampBetweenOrderBySnapshotTimestampAsc(any(), any()))
                .thenReturn(List.of(
                        snapshot(clusterA, dayOne, 100, 400),
                        snapshot(clusterB, dayOne, 50, 200),
                        // Three collection cycles on day two: only each cluster's last snapshot counts
                        snapshot(clusterA, dayTwo, 110, 400),
                        snapshot(clusterB, dayTwo, 60, 200),
                        snapshot(clusterA, dayTwo.plusHours(4), 120, 400),
                        snapshot(clusterA, dayTwo.plusHours(8), 130, 400)));

        ForecastingProjectionDto projection = forecastingService.generateProjection(30, null);

        assertThat(projection.getHistoricalPoints())
                .extracting(ForecastingProjectionDto.TrendPointDto::getCores)
                .containsExactly(150, 190);
        assertThat(projection.getCurrentCores()).isEqualTo(190);
        assertThat(projection.getTotalCapacityCores()).isEqualTo(600);
    }

    @Test
    void generateProjection_carriesForwardClustersWithoutASnapshotThatDay() {
        Cluster clusterA = Cluster.builder().id(UUID.randomUUID()).build();
        Cluster clusterB = Cluster.builder().id(UUID.randomUUID()).build();
        LocalDateTime dayOne = LocalDateTime.of(2026, 9, 1, 9, 0);

        when(snapshotRepository.findBySnapshotTimestampBetweenOrderBySnapshotTimestampAsc(any(), any()))
                .thenReturn(List.of(
                        snapshot(clusterA, dayOne, 100, 400),
                        snapshot(clusterB, dayOne, 50, 200),
                        snapshot(clusterA, dayOne.plusDays(10), 110, 400), // cluster B missed this day
                        snapshot(clusterA, dayOne.plusDays(20), 120, 400),
                        snapshot(clusterB, dayOne.plusDays(20), 70, 200)));

        ForecastingProjectionDto projection = forecastingService.generateProjection(30, null);

        assertThat(projection.getHistoricalPoints())
                .extracting(ForecastingProjectionDto.TrendPointDto::getCores)
                .containsExactly(150, 160, 190);
    }

    @Test
    void generateProjection_reportsReachedCapacityAsZeroRunway() {
        Cluster cluster = Cluster.builder().id(UUID.randomUUID()).build();
        LocalDateTime dayOne = LocalDateTime.of(2026, 9, 10, 9, 0);
        LocalDateTime dayTwo = LocalDateTime.of(2026, 9, 20, 9, 0);

        when(snapshotRepository.findBySnapshotTimestampBetweenOrderBySnapshotTimestampAsc(any(), any()))
                .thenReturn(List.of(
                        snapshot(cluster, dayOne, 90, 100),
                        snapshot(cluster, dayTwo, 105, 100)));

        ForecastingProjectionDto projection = forecastingService.generateProjection(30, null);

        assertThat(projection.getRunwayDaysCores()).isZero();
        assertThat(projection.getExhaustionDateCores()).isEqualTo(dayTwo.toLocalDate());
        assertThat(projection.isCapacityAlert()).isTrue();
    }

    @Test
    void generateProjection_forOneClusterUsesThatClustersCapacity() {
        Cluster cluster = Cluster.builder().id(UUID.randomUUID()).build();
        LocalDateTime dayOne = LocalDateTime.of(2026, 9, 10, 9, 0);
        LocalDateTime dayTwo = LocalDateTime.of(2026, 9, 20, 9, 0);

        when(snapshotRepository.findByClusterIdAndSnapshotTimestampBetweenOrderBySnapshotTimestampAsc(
                eq(cluster.getId()), any(), any()))
                .thenReturn(List.of(
                        snapshot(cluster, dayOne, 100, 200),
                        snapshot(cluster, dayTwo, 110, 200)));

        ForecastingProjectionDto projection = forecastingService.generateProjection(30, cluster.getId());

        // +1 core/day from 110 towards this cluster's 200 cores
        assertThat(projection.getTotalCapacityCores()).isEqualTo(200);
        assertThat(projection.getRunwayDaysCores()).isEqualTo(90);
        assertThat(projection.getExhaustionDateCores()).isEqualTo(LocalDate.of(2026, 12, 19));
    }

    @Test
    void generateProjection_withInsufficientHistory_reportsInsufficientData() {
        Cluster cluster = Cluster.builder().id(UUID.randomUUID()).build();

        when(snapshotRepository.findBySnapshotTimestampBetweenOrderBySnapshotTimestampAsc(any(), any()))
                .thenReturn(List.of(snapshot(cluster, LocalDateTime.of(2026, 9, 20, 9, 0), 80, 100)));

        ForecastingProjectionDto projection = forecastingService.generateProjection(30, null);

        assertThat(projection.getHorizonDays()).isEqualTo(30);
        assertThat(projection.isInsufficientData()).isTrue();
        assertThat(projection.getDataPoints()).isEqualTo(1);
        assertThat(projection.getCurrentCores()).isEqualTo(80);
        assertThat(projection.getTotalCapacityCores()).isEqualTo(100);
        assertThat(projection.getProjectedPoints()).isEmpty();
        assertThat(projection.getRunwayDaysCores()).isNull();
        assertThat(projection.getCoresRSquared()).isNull();
    }

    private static ClusterSnapshot snapshot(Cluster cluster, LocalDateTime timestamp, int allocatedCores, int totalCores) {
        return ClusterSnapshot.builder()
                .cluster(cluster)
                .snapshotTimestamp(timestamp)
                .allocatedCpuCores(allocatedCores)
                .totalCpuCores(totalCores)
                .build();
    }
}
