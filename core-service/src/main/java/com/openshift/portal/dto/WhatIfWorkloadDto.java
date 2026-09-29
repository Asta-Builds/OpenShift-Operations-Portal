package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.Environment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfWorkloadDto {
    private String name;
    private UUID targetClusterId;
    private String targetClusterName;
    private double requestedCpuCores;
    private double requestedMemoryGb;
    private double requestedStorageGb;
    private Environment environment;
    private String ownerTeam;
}
