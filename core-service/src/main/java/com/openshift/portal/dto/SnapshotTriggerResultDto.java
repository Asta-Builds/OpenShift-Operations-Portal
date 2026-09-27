package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotTriggerResultDto {
    private int clustersProcessed;
    private int snapshotsCreated;
    private long durationMs;
    private String status;
    private String message;
}
