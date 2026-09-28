package com.openshift.portal.notification;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.ReportDefinition;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.repository.ReportDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportSchedulerTest {

    /** A Monday. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 7, 0, 20);

    @Mock
    private ReportDefinitionRepository reportRepository;
    @Mock
    private NotificationQueue queue;

    private ReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReportScheduler(reportRepository, queue, new AcmProperties());
    }

    @Test
    void dueScheduleIsStartedOnceAndRecordsItsRunFirst() {
        ReportDefinition weekly = schedule("0 0 7 * * MON", NOW.minusDays(7), null);
        when(reportRepository.findByIsEnabledTrue()).thenReturn(List.of(weekly));

        assertThat(scheduler.runDue(NOW)).isEqualTo(1);

        InOrder order = inOrder(reportRepository, queue);
        order.verify(reportRepository).save(weekly);
        order.verify(queue).submitReport(new ReportJob(weekly.getId(), false));
        assertThat(weekly.getLastGeneratedAt()).isEqualTo(NOW);
        // A minute later it is no longer due
        assertThat(scheduler.runDue(NOW.plusMinutes(1))).isZero();
        verify(queue, times(1)).submitReport(any());
    }

    @Test
    void scheduleNotYetDueIsLeftAlone() {
        ReportDefinition later = schedule("0 0 9 * * MON", NOW.minusDays(1), null);
        when(reportRepository.findByIsEnabledTrue()).thenReturn(List.of(later));

        assertThat(scheduler.runDue(NOW)).isZero();

        verify(reportRepository, never()).save(any());
        verifyNoInteractions(queue);
    }

    @Test
    void missedOccurrencesRunOnceWhenThePortalIsBack() {
        // Daily at 06:00, last ran three days ago: due, but started only once
        ReportDefinition daily = schedule("0 0 6 * * *", NOW.minusDays(10), NOW.minusDays(3));
        when(reportRepository.findByIsEnabledTrue()).thenReturn(List.of(daily));

        assertThat(scheduler.runDue(NOW)).isEqualTo(1);
        assertThat(scheduler.runDue(NOW.plusMinutes(1))).isZero();
    }

    @Test
    void invalidCronIsSkippedWithoutStoppingOtherSchedules() {
        ReportDefinition broken = schedule("not a cron", NOW.minusDays(1), null);
        ReportDefinition due = schedule("0 0 7 * * *", NOW.minusDays(1), null);
        when(reportRepository.findByIsEnabledTrue()).thenReturn(List.of(broken, due));

        assertThat(scheduler.runDue(NOW)).isEqualTo(1);

        verify(queue).submitReport(new ReportJob(due.getId(), false));
    }

    private static ReportDefinition schedule(String cron, LocalDateTime createdAt, LocalDateTime lastRun) {
        return ReportDefinition.builder().id(UUID.randomUUID()).title("Report " + cron).reportType(ReportType.LICENSE_AUDIT)
                .cronSchedule(cron).recipients("ops@example.com").isEnabled(true).createdAt(createdAt)
                .lastGeneratedAt(lastRun).build();
    }
}
