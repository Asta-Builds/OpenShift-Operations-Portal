package com.openshift.portal.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportSchedulesTest {

    @Test
    void unixFiveFieldCronGetsASecondsField() {
        assertThat(ReportSchedules.normalizeCron("0 7 * * MON")).isEqualTo("0 0 7 * * MON");
        assertThat(ReportSchedules.normalizeCron("  30   6  1 * *  ")).isEqualTo("0 30 6 1 * *");
        assertThat(ReportSchedules.normalizeCron("0 0 7 * * MON-FRI")).isEqualTo("0 0 7 * * MON-FRI");
    }

    @Test
    void invalidCronIsRejected() {
        assertThatThrownBy(() -> ReportSchedules.normalizeCron("every monday")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid cron schedule");
        assertThatThrownBy(() -> ReportSchedules.normalizeCron("0 25 * * *")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportSchedules.normalizeCron(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextRunFollowsTheSchedule() {
        // 2026-09-28 is a Monday
        LocalDateTime mondayMorning = LocalDateTime.of(2026, 9, 28, 6, 59, 30);

        assertThat(ReportSchedules.nextRun("0 7 * * MON", mondayMorning)).isEqualTo(LocalDateTime.of(2026, 9, 28, 7, 0));
        assertThat(ReportSchedules.nextRun("0 7 * * MON", mondayMorning.plusMinutes(1)))
                .isEqualTo(LocalDateTime.of(2026, 10, 5, 7, 0));
    }

    @Test
    void recipientsAreSplitOnCommasSemicolonsAndSpacesWithoutDuplicates() {
        assertThat(ReportSchedules.parseRecipients(" ops@example.com; finops@example.com,ops@example.com  cio@example.com "))
                .containsExactly("ops@example.com", "finops@example.com", "cio@example.com");
    }

    @Test
    void invalidRecipientsAreRejected() {
        assertThatThrownBy(() -> ReportSchedules.parseRecipients("ops@example.com, not-an-address"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("'not-an-address'");
        assertThatThrownBy(() -> ReportSchedules.parseRecipients("ops@@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportSchedules.parseRecipients(" , ; "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one");
        String tooMany = String.join(",", Collections.nCopies(ReportSchedules.MAX_RECIPIENTS + 1, "x"))
                .replace("x", "u@example.com");
        // Duplicates collapse, so make them distinct
        StringBuilder distinct = new StringBuilder();
        for (int i = 0; i <= ReportSchedules.MAX_RECIPIENTS; i++) {
            distinct.append("user").append(i).append("@example.com,");
        }
        assertThat(ReportSchedules.parseRecipients(tooMany)).hasSize(1);
        assertThatThrownBy(() -> ReportSchedules.parseRecipients(distinct.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most");
    }
}
