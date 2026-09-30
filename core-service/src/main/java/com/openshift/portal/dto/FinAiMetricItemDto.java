package com.openshift.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Key performance metric highlight calculated by FinAI")
public class FinAiMetricItemDto {

    @Schema(description = "Metric label", example = "Économies potentielles")
    private String label;

    @Schema(description = "Formatted metric value", example = "$21,692.86 / mois")
    private String value;

    @Schema(description = "Display badge type", example = "SAVINGS")
    private String type; // SAVINGS, CORES, WARNING, SUCCESS, INFO
}
