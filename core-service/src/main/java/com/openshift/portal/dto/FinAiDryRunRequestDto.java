package com.openshift.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to simulate server dry-run on OpenShift API Server")
public class FinAiDryRunRequestDto {

    @NotBlank
    @Schema(description = "Target namespace", example = "spark-batch-analytics")
    private String namespace;

    @Schema(description = "Target cluster ID", example = "ocp-ai-training-prod")
    private String clusterId;

    @Schema(description = "Raw oc patch or apply command being tested")
    private String command;

    @Schema(description = "ResourceQuota YAML manifest content")
    private String resourceQuotaYaml;
}
