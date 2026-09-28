package com.openshift.portal.notification;

import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.Notification;
import com.openshift.portal.domain.entity.ReportDefinition;
import com.openshift.portal.domain.enums.NotificationKind;
import com.openshift.portal.domain.enums.ReportFormat;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.dto.LicenseAuditDto;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.repository.ReportDefinitionRepository;
import com.openshift.portal.service.LicensingService;
import com.openshift.portal.service.PdfReportGeneratorService;
import com.openshift.portal.service.ReportSchedules;
import com.openshift.portal.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs one report schedule: generates its CSV or PDF, records the notification and hands the email on. Every outcome
 * is recorded, including a report that could not be generated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDeliveryService {

    /** Types that have an export; GROWTH_FORECAST does not yet. */
    public static final Set<ReportType> SCHEDULABLE_TYPES =
            Set.of(ReportType.FLEET_CAPACITY, ReportType.LICENSE_AUDIT, ReportType.COST_ATTRIBUTION);

    private static final int MAX_LISTED_CLUSTERS = 10;

    private final ReportDefinitionRepository reportRepository;
    private final ReportingService reportingService;
    private final PdfReportGeneratorService pdfReportGenerator;
    private final LicensingService licensingService;
    private final ClusterSnapshotRepository snapshotRepository;
    private final NotificationService notifications;
    private final MailContentService mailContent;
    private final NotificationQueue queue;

    public void generate(ReportJob job) {
        ReportDefinition report = reportRepository.findById(job.reportId()).orElse(null);
        if (report == null) {
            log.info("Report schedule {} was deleted before it ran", job.reportId());
            return;
        }
        if (!job.manual() && !Boolean.TRUE.equals(report.getIsEnabled())) {
            log.info("Report schedule '{}' was disabled before it ran", report.getTitle());
            return;
        }
        String subject = "[OpenShift Operations Portal] " + report.getTitle();

        List<String> recipients;
        byte[] attachment;
        String attachmentName;
        String html;
        try {
            recipients = ReportSchedules.parseRecipients(report.getRecipients());
            if (!SCHEDULABLE_TYPES.contains(report.getReportType())) {
                throw new IllegalArgumentException(report.getReportType() + " reports cannot be exported yet");
            }
            boolean pdf = report.getFormat() == ReportFormat.PDF;
            attachment = pdf ? pdfReportGenerator.generatePdfReport(report.getReportType())
                    : reportingService.generateCsvReport(report.getReportType());
            attachmentName = String.format("openshift-%s-%s.%s", report.getReportType().name().toLowerCase(Locale.ROOT),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")), pdf ? "pdf" : "csv");
            html = mailContent.report(report.getTitle(), label(report.getReportType()), attachmentName,
                    summary(report.getReportType()));
        } catch (Exception e) {
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Report schedule '{}' could not be prepared: {}", report.getTitle(), error);
            notifications.failed(NotificationKind.SCHEDULED_REPORT, report.getId(), subject, report.getRecipients(),
                    "The report could not be generated: " + error);
            return;
        }

        Notification notification = notifications.queued(NotificationKind.SCHEDULED_REPORT, report.getId(), subject,
                recipients, null);
        queue.submitMail(new OutgoingMail(notification.getId(), recipients, subject, html, attachmentName,
                report.getFormat() == ReportFormat.PDF ? "application/pdf" : "text/csv", attachment));
    }

    /** A few lines for the email body, so the headline figures are readable without opening the attachment. */
    private List<String> summary(ReportType type) {
        List<String> lines = new ArrayList<>();
        switch (type) {
            case LICENSE_AUDIT -> {
                LicenseAuditDto audit = licensingService.generateLicenseAudit();
                List<LicenseAuditDto.NodeDataGap> gaps = audit.getClustersWithoutNodeData();
                lines.add("Compliance status: " + audit.getComplianceStatus());
                lines.add(gaps.isEmpty()
                        ? "License cores: " + audit.getTotalLicenseCores()
                        : "License cores: at least " + audit.getTotalLicenseCores() + " (" + audit.getClustersCounted()
                        + " of " + (audit.getClustersCounted() + gaps.size()) + " clusters have node data)");
                lines.add("High watermark: " + audit.getHighWatermarkCores() + " cores; contracted cap: "
                        + audit.getLicensedCapCores() + " cores");
                if (!gaps.isEmpty()) {
                    List<String> names = gaps.stream().map(LicenseAuditDto.NodeDataGap::clusterName).toList();
                    lines.add("Clusters without node data: " + String.join(", ", names.subList(0, Math.min(names.size(), MAX_LISTED_CLUSTERS)))
                            + (names.size() > MAX_LISTED_CLUSTERS ? " and " + (names.size() - MAX_LISTED_CLUSTERS) + " more" : ""));
                }
            }
            case FLEET_CAPACITY -> {
                List<ClusterSnapshot> latest = snapshotRepository.findLatestSnapshotsForAllClusters();
                int totalCores = latest.stream().mapToInt(s -> s.getTotalCpuCores() != null ? s.getTotalCpuCores() : 0).sum();
                BigDecimal requestedCores = sum(latest.stream().map(ClusterSnapshot::getAllocatedCpuCores).toList());
                BigDecimal totalMemory = sum(latest.stream().map(ClusterSnapshot::getTotalMemoryGb).toList());
                BigDecimal requestedMemory = sum(latest.stream().map(ClusterSnapshot::getAllocatedMemoryGb).toList());
                lines.add("Clusters: " + latest.size());
                lines.add("CPU requested: " + requestedCores.stripTrailingZeros().toPlainString() + " of " + totalCores + " cores");
                lines.add("Memory requested: " + requestedMemory.stripTrailingZeros().toPlainString() + " of "
                        + totalMemory.stripTrailingZeros().toPlainString() + " GB");
            }
            case COST_ATTRIBUTION -> lines.add("Requests and usage by team and cost center over the last 30 days.");
            default -> {
            }
        }
        return lines;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String label(ReportType type) {
        return switch (type) {
            case FLEET_CAPACITY -> "Fleet capacity";
            case LICENSE_AUDIT -> "License audit";
            case COST_ATTRIBUTION -> "Cost attribution";
            case GROWTH_FORECAST -> "Growth forecast";
        };
    }
}
