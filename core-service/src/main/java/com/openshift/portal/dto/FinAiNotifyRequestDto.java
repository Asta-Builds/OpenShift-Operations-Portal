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
@Schema(description = "Request to dispatch FinAI notification to Slack or Microsoft Teams")
public class FinAiNotifyRequestDto {

    @NotBlank
    @Schema(description = "Target collaboration platform: SLACK or TEAMS", example = "SLACK")
    private String platform;

    @Schema(description = "Channel name or Webhook identifier", example = "#finops-alerts")
    private String channel;

    @Schema(description = "Headline or subject", example = "Optimisation FinOps : 1 691 $/mois identifiés sur spark-batch-analytics")
    private String headline;

    @Schema(description = "Summary message body")
    private String summary;

    @Schema(description = "Potential monthly savings in USD", example = "1691.82")
    private Double savingsUsd;

    @Schema(description = "Target namespace", example = "spark-batch-analytics")
    private String namespace;
}
