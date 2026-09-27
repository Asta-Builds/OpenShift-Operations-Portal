package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseAuditDto {
    private int totalLicenseCores;
    private int licensedCapCores;
    private int highWatermarkCores;
    private boolean complianceBreach;
    private int workerNodesCount;
    private int masterNodesCount;
    private int bareMetalCores;
    private int virtualCores;
    private Map<String, Integer> coresByEnvironment;
    private Map<String, Integer> coresByOwnerTeam;
    private Map<String, Integer> coresByInfrastructure;
}
