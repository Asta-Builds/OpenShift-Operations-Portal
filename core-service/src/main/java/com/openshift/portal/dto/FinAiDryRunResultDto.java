package com.openshift.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Server dry-run verification result simulating OpenShift API server response")
public class FinAiDryRunResultDto {

    @Schema(description = "Whether the dry-run succeeded without syntax or quota violations", example = "true")
    private boolean success;

    @Schema(description = "Simulated API response status", example = "resourcequota/compute-resources configured (server dry run)")
    private String status;

    @Schema(description = "Human-readable diagnostic output")
    private String message;

    @Schema(description = "Evaluated pods currently active in namespace", example = "18")
    private int podsEvaluated;

    @Schema(description = "Pods that would violate limits or trigger throttling", example = "0")
    private int podsExceedingLimits;

    @Builder.Default
    @Schema(description = "Advisory notices and safety checks")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "Timestamp of dry-run execution")
    private Instant timestamp;
}
