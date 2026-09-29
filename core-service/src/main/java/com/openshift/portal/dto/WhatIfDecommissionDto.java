package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfDecommissionDto {
    private UUID sourceClusterId;
    private String sourceClusterName;
    private UUID targetClusterId;
    private String targetClusterName;
}
