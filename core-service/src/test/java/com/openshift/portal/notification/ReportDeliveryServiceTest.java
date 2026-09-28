package com.openshift.portal.notification;

import com.openshift.portal.domain.entity.ReportDefinition;
import com.openshift.portal.domain.enums.NotificationKind;
import com.openshift.portal.domain.enums.ReportFormat;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.ReportDefinitionRepository;
import com.openshift.portal.service.LicensingService;
import com.openshift.portal.service.PdfReportGeneratorService;
import com.openshift.portal.service.ReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportDeliveryServiceTest {

    @Mock
    private ReportDefinitionRepository reportRepository;
    @Mock
    private ReportingService reportingService;
    @Mock
    private PdfReportGeneratorService pdfReportGenerator;
    @Mock
    private LicensingService licensingService;
    @Mock
    private ClusterSnapshotRepository snapshotRepository;
    @Mock
    private NotificationService notifications;
    @Mock
    private MailContentService mailContent;
    @Mock
    private NotificationQueue queue;

    private ReportDeliveryService service;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReportDeliveryService(reportRepository, reportingService, pdfReportGenerator, licensingService,
                snapshotRepository, notifications, mailContent, queue);
    }

    @Test
    void reportThatFailsToGenerateIsRecordedAsFailedAndNotEmailed() {
        when(reportRepository.findById(id)).thenReturn(Optional.of(report(true, ReportType.FLEET_CAPACITY)));
        when(pdfReportGenerator.generatePdfReport(ReportType.FLEET_CAPACITY)).thenThrow(new IllegalStateException("font missing"));

        service.generate(new ReportJob(id, false));

        verify(notifications).failed(eq(NotificationKind.SCHEDULED_REPORT), eq(id), contains("Weekly"),
                eq("ops@example.com"), eq("The report could not be generated: font missing"));
        verifyNoInteractions(queue);
    }

    @Test
    void scheduleDisabledAfterItWasQueuedDoesNotRun() {
        when(reportRepository.findById(id)).thenReturn(Optional.of(report(false, ReportType.FLEET_CAPACITY)));

        service.generate(new ReportJob(id, false));

        verifyNoInteractions(pdfReportGenerator, notifications, queue);
    }

    @Test
    void reportTypeWithoutAnExportIsRecordedAsFailed() {
        when(reportRepository.findById(id)).thenReturn(Optional.of(report(true, ReportType.GROWTH_FORECAST)));

        service.generate(new ReportJob(id, true));

        verify(notifications).failed(eq(NotificationKind.SCHEDULED_REPORT), eq(id), anyString(), anyString(),
                contains("cannot be exported yet"));
        verifyNoInteractions(pdfReportGenerator, queue);
    }

    private ReportDefinition report(boolean enabled, ReportType type) {
        return ReportDefinition.builder().id(id).title("Weekly fleet").reportType(type).format(ReportFormat.PDF)
                .cronSchedule("0 0 7 * * MON").recipients("ops@example.com").isEnabled(enabled)
                .createdAt(LocalDateTime.now()).build();
    }
}
