package com.openshift.portal.notification;

import com.openshift.portal.domain.entity.ReportDefinition;
import com.openshift.portal.domain.enums.ReportFormat;
import com.openshift.portal.dto.ReportScheduleDto;
import com.openshift.portal.dto.ReportScheduleRequests;
import com.openshift.portal.exception.ResourceNotFoundException;
import com.openshift.portal.repository.NotificationRepository;
import com.openshift.portal.repository.ReportDefinitionRepository;
import com.openshift.portal.service.ReportSchedules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Creates, changes and lists report schedules; the scheduler runs them. */
@Service
@RequiredArgsConstructor
public class ReportScheduleService {

    private final ReportDefinitionRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationQueue queue;

    @Transactional(readOnly = true)
    public List<ReportScheduleDto> list() {
        return reportRepository.findAll(Sort.by("createdAt")).stream().map(this::toDto).toList();
    }

    @Transactional
    public ReportScheduleDto create(ReportScheduleRequests.Create request) {
        requireSchedulable(request.getReportType());
        ReportDefinition report = ReportDefinition.builder()
                .title(request.getTitle().trim())
                .reportType(request.getReportType())
                .format(request.getFormat() != null ? request.getFormat() : ReportFormat.PDF)
                .cronSchedule(cron(request.getCronSchedule()))
                .recipients(recipients(request.getRecipients()))
                .isEnabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        return toDto(reportRepository.save(report));
    }

    @Transactional
    public ReportScheduleDto update(UUID id, ReportScheduleRequests.Update request) {
        ReportDefinition report = find(id);
        if (request.getTitle() != null) {
            report.setTitle(request.getTitle().trim());
        }
        if (request.getFormat() != null) {
            report.setFormat(request.getFormat());
        }
        if (request.getCronSchedule() != null) {
            report.setCronSchedule(cron(request.getCronSchedule()));
        }
        if (request.getRecipients() != null) {
            report.setRecipients(recipients(request.getRecipients()));
        }
        if (request.getEnabled() != null) {
            if (request.getEnabled() && !Boolean.TRUE.equals(report.getIsEnabled())) {
                // Re-enabled: counts from now, so occurrences missed while disabled are not sent at once
                report.setLastGeneratedAt(LocalDateTime.now());
            }
            report.setIsEnabled(request.getEnabled());
        }
        return toDto(reportRepository.save(report));
    }

    @Transactional
    public void delete(UUID id) {
        reportRepository.delete(find(id));
    }

    /** Sends the report now, whether the schedule is enabled or not; its timing is left alone. */
    public void runNow(UUID id) {
        ReportDefinition report = find(id);
        requireSchedulable(report.getReportType());
        queue.submitReport(new ReportJob(report.getId(), true));
    }

    private ReportDefinition find(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report schedule not found: " + id));
    }

    private ReportScheduleDto toDto(ReportDefinition report) {
        boolean enabled = Boolean.TRUE.equals(report.getIsEnabled());
        LocalDateTime nextRun = null;
        if (enabled && report.getCronSchedule() != null) {
            LocalDateTime since = report.getLastGeneratedAt() != null ? report.getLastGeneratedAt() : report.getCreatedAt();
            try {
                nextRun = ReportSchedules.nextRun(report.getCronSchedule(), since);
            } catch (IllegalArgumentException ignored) {
                // Legacy rows created before validation: shown without a next run
            }
        }
        ReportScheduleDto.LastDelivery lastDelivery = notificationRepository
                .findTopByReportIdOrderByCreatedAtDescIdDesc(report.getId())
                .map(n -> new ReportScheduleDto.LastDelivery(n.getStatus(),
                        n.getSentAt() != null ? n.getSentAt() : n.getCreatedAt(), n.getDetail()))
                .orElse(null);
        return new ReportScheduleDto(report.getId(), report.getTitle(), report.getReportType(), report.getFormat(),
                report.getCronSchedule(), report.getRecipients(), enabled, report.getCreatedAt(),
                report.getLastGeneratedAt(), nextRun, lastDelivery);
    }

    private static void requireSchedulable(com.openshift.portal.domain.enums.ReportType type) {
        if (!ReportDeliveryService.SCHEDULABLE_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type + " reports cannot be exported or scheduled yet");
        }
    }

    private static String cron(String value) {
        try {
            return ReportSchedules.normalizeCron(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cronSchedule: " + e.getMessage());
        }
    }

    private static String recipients(String value) {
        try {
            return String.join(", ", ReportSchedules.parseRecipients(value));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipients: " + e.getMessage());
        }
    }
}
