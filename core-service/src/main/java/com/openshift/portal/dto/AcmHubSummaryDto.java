package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.HubStatus;
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
}
