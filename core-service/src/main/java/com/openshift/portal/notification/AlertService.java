package com.openshift.portal.notification;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.Notification;
import com.openshift.portal.domain.enums.NotificationKind;
import com.openshift.portal.dto.ForecastingProjectionDto;
import com.openshift.portal.dto.LicenseAuditDto;
import com.openshift.portal.service.ForecastingService;
import com.openshift.portal.service.LicensingService;
import com.openshift.portal.service.ReportSchedules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Emails the alert recipients when the license high watermark exceeds the cap, or when CPU or memory requests are
 * projected to reach capacity within the runway threshold. Checked after every collection, sent at most once a day
 * per alert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    /** Looks back this many days for the growth trend; the Growth Forecast page's default. */
    static final int RUNWAY_TREND_DAYS = 30;

    private final LicensingService licensingService;
    private final ForecastingService forecastingService;
    private final NotificationService notifications;
    private final MailContentService mailContent;
    private final NotificationQueue queue;
    private final AcmProperties properties;

    public void evaluate() {
        String configured = properties.getNotifications().getAlertRecipients();
        if (configured == null || configured.isBlank()) {
            return;
        }
        List<String> recipients;
        try {
            recipients = ReportSchedules.parseRecipients(configured);
        } catch (IllegalArgumentException e) {
            log.warn("Alerts are not sent: openshift.portal.notifications.alert-recipients is invalid: {}", e.getMessage());
            return;
        }
        LocalDate today = LocalDate.now();
        checkLicense(recipients, today);
        checkRunway(recipients, today);
    }

    private void checkLicense(List<String> recipients, LocalDate today) {
        LicenseAuditDto audit = licensingService.generateLicenseAudit();
        if (audit.getComplianceStatus() != LicenseAuditDto.ComplianceStatus.BREACH) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("The license high watermark is " + audit.getHighWatermarkCores() + " cores; the contracted cap is "
                + audit.getLicensedCapCores() + " cores.");
        lines.add("Current worker cores: " + (audit.getClustersWithoutNodeData().isEmpty() ? "" : "at least ")
                + audit.getTotalLicenseCores() + ".");
        if (!audit.getClustersWithoutNodeData().isEmpty()) {
            lines.add(audit.getClustersWithoutNodeData().size()
                    + " cluster(s) have no node data and are not counted, so the real figure may be higher.");
        }
        send(NotificationKind.LICENSE_BREACH, today, "License cap exceeded: " + audit.getHighWatermarkCores() + " of "
                + audit.getLicensedCapCores() + " cores", lines, "/licensing", recipients);
    }

    private void checkRunway(List<String> recipients, LocalDate today) {
        ForecastingProjectionDto projection = forecastingService.generateProjection(RUNWAY_TREND_DAYS, null);
        if (projection.isInsufficientData()) {
            return;
        }
        int threshold = properties.getNotifications().getRunwayAlertDays();
        List<String> lines = new ArrayList<>();
        Integer cpu = projection.getRunwayDaysCores();
        Integer memory = projection.getRunwayDaysMemory();
        if (cpu != null && cpu <= threshold) {
            lines.add("CPU requests reach the fleet's " + projection.getTotalCapacityCores() + " cores in " + cpu
                    + " day(s), around " + projection.getExhaustionDateCores() + ".");
        }
        if (memory != null && memory <= threshold) {
            lines.add("Memory requests reach the fleet's " + Math.round(projection.getTotalCapacityMemoryGb())
                    + " GB in " + memory + " day(s), around " + projection.getExhaustionDateMemory() + ".");
        }
        if (lines.isEmpty()) {
            return;
        }
        lines.add("Projected from the last " + RUNWAY_TREND_DAYS + " days of growth; the alert threshold is "
                + threshold + " days.");
        int days = Math.min(cpu != null ? cpu : Integer.MAX_VALUE, memory != null ? memory : Integer.MAX_VALUE);
        send(NotificationKind.CAPACITY_RUNWAY, today, "Capacity runway: " + days + " day(s) left", lines,
                "/forecasting", recipients);
    }

    private void send(NotificationKind kind, LocalDate today, String subject, List<String> lines, String portalPath,
                      List<String> recipients) {
        String dedupeKey = kind + ":" + today;
        if (notifications.exists(dedupeKey)) {
            return;
        }
        String fullSubject = "[OpenShift Operations Portal] " + subject;
        Notification notification;
        try {
            notification = notifications.queued(kind, null, fullSubject, recipients, dedupeKey);
        } catch (DataIntegrityViolationException e) {
            return; // Another replica recorded today's alert first
        }
        log.warn("Alert: {}", subject);
        queue.submitMail(new OutgoingMail(notification.getId(), recipients, fullSubject,
                mailContent.alert(subject, lines, portalPath), null, null, null));
    }
}
