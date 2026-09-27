package com.openshift.portal.service;

import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.ReportType;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfReportGeneratorServiceTest {

    @Mock
    private ClusterSnapshotRepository snapshotRepository;

    @Mock
    private AttributionService attributionService;

    private PdfReportGeneratorService pdfService;

    @BeforeEach
    void setUp() {
        pdfService = new PdfReportGeneratorService(snapshotRepository, attributionService);
    }

    @Test
    void generatePdfReport_createsValidPdfBytes() {
        Cluster cluster = Cluster.builder()
                .id(UUID.randomUUID())
                .clusterName("ocp-prod-test")
                .environment(Environment.PRODUCTION)
                .infrastructureType(InfrastructureType.BARE_METAL)
                .build();

        ClusterSnapshot snapshot = ClusterSnapshot.builder()
                .cluster(cluster)
                .snapshotTimestamp(LocalDateTime.now())
                .totalCpuCores(64)
                .allocatedCpuCores(BigDecimal.valueOf(32))
                .totalMemoryGb(BigDecimal.valueOf(256.0))
                .allocatedMemoryGb(BigDecimal.valueOf(128.0))
                .totalStorageGb(BigDecimal.valueOf(2000.0))
                .allocatedStorageGb(BigDecimal.valueOf(1000.0))
                .workerNodes(4)
                .totalNodes(7)
                .licenseCoresCount(64)
                .build();

        when(snapshotRepository.findLatestSnapshotsForAllClusters()).thenReturn(List.of(snapshot));

        byte[] pdfBytes = pdfService.generatePdfReport(ReportType.FLEET_CAPACITY);

        assertThat(pdfBytes).isNotEmpty();
        // PDF documents start with %PDF
        String header = new String(pdfBytes, 0, 4);
        assertThat(header).isEqualTo("%PDF");
    }
}
