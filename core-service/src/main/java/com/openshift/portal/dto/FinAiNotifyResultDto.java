package com.openshift.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of Slack / Teams notification webhook dispatch")
public class FinAiNotifyResultDto {

    @Schema(description = "Whether the webhook was successfully dispatched", example = "true")
    private boolean dispatched;

    @Schema(description = "Target platform", example = "SLACK")
    private String targetPlatform;

    @Schema(description = "Channel or destination", example = "#finops-alerts")
    private String destination;

    @Schema(description = "JSON payload preview sent to webhook")
    private String payloadPreview;

    @Schema(description = "Confirmation message")
    private String message;

    @Schema(description = "Timestamp of dispatch")
    private Instant timestamp;
}
