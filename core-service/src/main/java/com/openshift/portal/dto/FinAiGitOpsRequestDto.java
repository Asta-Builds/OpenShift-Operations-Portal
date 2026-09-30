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
@Schema(description = "Request to generate ArgoCD / OpenShift GitOps manifest repository structure")
public class FinAiGitOpsRequestDto {

    @NotBlank
    @Schema(description = "Target namespace", example = "spark-batch-analytics")
    private String namespace;

    @Schema(description = "Target cluster ID", example = "ocp-ai-training-prod")
    private String clusterId;

    @Schema(description = "Requested CPU quota (e.g. 14.6c)", example = "14.6c")
    private String cpuRequest;

    @Schema(description = "Requested Memory quota (e.g. 69Gi)", example = "69Gi")
    private String memoryRequest;
}
