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
@Schema(description = "User prompt and contextual telemetry passed to the FinAI Copilot reasoning engine")
public class FinAiPromptRequestDto {

    @Schema(description = "Natural language prompt or operator question", example = "Comment remédier au gaspillage sur spark-batch-analytics ?")
    private String prompt;

    @Schema(description = "Active UI portal page context", example = "FINOPS")
    private String pageContext;

    @Schema(description = "Optional active cluster identifier for focused context", example = "ocp-ai-training-prod")
    private String selectedClusterId;

    @Schema(description = "Optional active namespace for targeted quota generation", example = "spark-batch-analytics")
    private String selectedNamespace;
}
