package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
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
public class ClusterSummaryDto {
    private UUID id;
    private String clusterName;
    private String acmHubName;
    private Environment environment;
    private String ownerTeamName;
    private InfrastructureType infrastructureType;
    private String openshiftVersion;
    private String status;
    private int totalCores;
    private int allocatedCores;
    private double totalMemoryGb;
    private double allocatedMemoryGb;
    private int licenseCores;
    private LocalDateTime lastSnapshotTime;
}
