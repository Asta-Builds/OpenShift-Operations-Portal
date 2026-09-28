package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.ReportFormat;
import com.openshift.portal.domain.enums.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request bodies of the report schedule endpoints. Cron and recipients are checked by the service. */
public final class ReportScheduleRequests {

    private ReportScheduleRequests() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Create {
        @NotBlank
        @Size(max = 255)
        private String title;

        @NotNull
        private ReportType reportType;

        /** PDF when omitted. */
        private ReportFormat format;

        /** Five-field Unix cron (e.g. {@code 0 7 * * MON}) or six-field Spring cron, in the portal's time zone. */
        @NotBlank
        @Size(max = 100)
        private String cronSchedule;

        /** Addresses separated by commas, semicolons or spaces. */
        @NotBlank
        @Size(max = 4000)
        private String recipients;
    }

    /** Only the fields present are changed. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Update {
        @Size(min = 1, max = 255)
        private String title;
        private ReportFormat format;
        @Size(min = 1, max = 100)
        private String cronSchedule;
        @Size(min = 1, max = 4000)
        private String recipients;
        private Boolean enabled;
    }
}
