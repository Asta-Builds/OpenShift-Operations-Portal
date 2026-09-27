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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        assertThat(projection.getCurrentCores()).isEqualTo(120);
        // Slope is 1.0 core per day. At +30 days, projected is 120 + 30 = 150 cores.
        assertThat(projection.getProjectedCores()).isEqualTo(150);
        assertThat(projection.getEstimatedGrowthPercent()).isEqualTo(25.0); // (150-120)/120 = 25%
        assertThat(projection.getDailyGrowthRateCores()).isEqualTo(1.0);
        assertThat(projection.getHistoricalPoints()).hasSize(3);
        assertThat(projection.getProjectedPoints()).isNotEmpty();
    }

    @Test
    void generateProjection_withInsufficientHistory_returnsSyntheticBaseline() {
        when(snapshotRepository.findBySnapshotTimestampBetweenOrderBySnapshotTimestampAsc(any(), any()))
                .thenReturn(List.of());
        when(snapshotRepository.findLatestSnapshotsForAllClusters()).thenReturn(List.of());

        ForecastingProjectionDto projection = forecastingService.generateProjection(30, null);

        assertThat(projection.getHorizonDays()).isEqualTo(30);
        assertThat(projection.getCurrentCores()).isGreaterThan(0);
        assertThat(projection.getProjectedCores()).isGreaterThan(projection.getCurrentCores());
        assertThat(projection.getConfidenceScore()).isGreaterThan(0.80);
    }
}
