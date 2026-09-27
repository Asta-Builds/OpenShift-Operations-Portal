package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.SyncStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcmHubSummaryDto {
    private UUID id;
    private String name;
    private String apiUrl;
    private HubStatus status;
    private LocalDateTime lastSyncTimestamp;
    private int consecutiveFailures;
    /** Circuit breaker state held in memory by the instance that served the request. */
    private String circuitBreakerState;
    private SyncRunDto latestSyncRun;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncRunDto {
        private SyncStatus status;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private int attempts;
        private int clustersOk;
        private int clustersFailed;
        private String errorMessage;
    }
}
