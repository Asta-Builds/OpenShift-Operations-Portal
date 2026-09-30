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
@Schema(description = "Interactive Before vs After Kubernetes YAML manifest diff comparison")
public class FinAiYamlDiffDto {

    @Schema(description = "Kubernetes resource kind", example = "ResourceQuota")
    private String resourceKind;

    @Schema(description = "Resource name", example = "compute-resources")
    private String resourceName;

    @Schema(description = "Target namespace", example = "spark-batch-analytics")
    private String targetNamespace;

    @Schema(description = "Baseline/Current overprovisioned YAML definition")
    private String beforeYaml;

    @Schema(description = "Target/Optimized rightsized YAML definition")
    private String afterYaml;

    @Schema(description = "CPU reduction delta", example = "-56.5c (-89%)")
    private String cpuDelta;

    @Schema(description = "Memory reduction delta", example = "-152Gi (-84%)")
    private String memoryDelta;

    @Schema(description = "Monthly cost delta", example = "-$2,288.16 / mois")
    private String costDelta;

    @Schema(description = "Safety headroom included", example = "+20% Buffer P99")
    private String safetyMargin;
}
