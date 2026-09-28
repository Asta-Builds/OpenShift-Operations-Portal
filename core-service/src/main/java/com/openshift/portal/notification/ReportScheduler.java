package com.openshift.portal.notification;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.ReportDefinition;
import com.openshift.portal.repository.ReportDefinitionRepository;
import com.openshift.portal.service.ReportSchedules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Starts the report schedules that are due. Checked every minute on one replica; a schedule missed while the portal
 * was down runs once when it is back, not once per missed occurrence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private final ReportDefinitionRepository reportRepository;
    private final NotificationQueue queue;
    private final AcmProperties properties;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    @SchedulerLock(name = "report-schedules", lockAtMostFor = "PT10M")
    public void runDueSchedules() {
        if (properties.getNotifications().isSchedulerEnabled()) {
            runDue(LocalDateTime.now());
        }
    }

    /** Starts every enabled schedule whose next run after its last one is not after {@code now}; returns how many. */
    public int runDue(LocalDateTime now) {
        int started = 0;
        for (ReportDefinition report : reportRepository.findByIsEnabledTrue()) {
            if (report.getCronSchedule() == null || report.getCronSchedule().isBlank()) {
                continue;
            }
            LocalDateTime since = report.getLastGeneratedAt() != null ? report.getLastGeneratedAt() : report.getCreatedAt();
            LocalDateTime next;
            try {
                next = ReportSchedules.nextRun(report.getCronSchedule(), since);
            } catch (IllegalArgumentException e) {
                log.warn("Report schedule '{}' has an invalid cron and does not run: {}", report.getTitle(), e.getMessage());
                continue;
            }
            if (next == null || next.isAfter(now)) {
                continue;
            }
            // Recorded before the run starts: a failing report is logged and waits for its next occurrence
            // instead of being retried every minute
            report.setLastGeneratedAt(now);
            reportRepository.save(report);
            log.info("Report schedule '{}' is due (was due {}), starting it", report.getTitle(), next);
            try {
                queue.submitReport(new ReportJob(report.getId(), false));
                started++;
            } catch (RuntimeException e) {
                log.error("Could not start report schedule '{}'", report.getTitle(), e);
            }
        }
        return started;
    }
}
