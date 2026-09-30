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
@Schema(description = "Single data point for FinAI interactive visual charts")
public class FinAiChartDataPointDto {

    @Schema(description = "Label or category name", example = "payment-gateway")
    private String label;

    @Schema(description = "Primary numerical value", example = "2288.16")
    private Double value;

    @Schema(description = "Secondary comparative value (e.g. baseline or actual)", example = "1420.00")
    private Double secondaryValue;

    @Schema(description = "Display color in hex or design token", example = "#EF4444")
    private String color;

    @Schema(description = "Formatted display value", example = "$2,288 / mo")
    private String formattedValue;
}
