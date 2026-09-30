package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfPresetDto {
    private String id;
    private String title;
    private String category;
    private String description;
    private String icon;
    private String badge;
    private WhatIfSimulationRequestDto request;
}
