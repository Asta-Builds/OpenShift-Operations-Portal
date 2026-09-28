package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.NotificationStatus;
import com.openshift.portal.domain.enums.ReportFormat;
import com.openshift.portal.domain.enums.ReportType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A report schedule with when it runs next and how its latest delivery went.
 *
 * @param lastRunAt    when the schedule last came due; "send now" runs leave it alone
 * @param nextRunAt    null while disabled
 * @param lastDelivery the latest email of this schedule, scheduled or sent now; null before the first
 */
public record ReportScheduleDto(
        UUID id,
        String title,
        ReportType reportType,
        ReportFormat format,
        String cronSchedule,
        String recipients,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime lastRunAt,
        LocalDateTime nextRunAt,
        LastDelivery lastDelivery) {

    public record LastDelivery(NotificationStatus status, LocalDateTime at, String detail) {
    }
}
