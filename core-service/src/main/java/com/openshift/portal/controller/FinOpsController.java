package com.openshift.portal.controller;

import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.FinOpsEfficiencyRating;
import com.openshift.portal.dto.*;
import com.openshift.portal.service.FinOpsService;
import com.openshift.portal.service.WhatIfSimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/finops")
@RequiredArgsConstructor
@Tag(name = "FinOps & Rightsizing", description = "FinOps cost allocation, resource waste quantification and Kubernetes rightsizing recommendations")
public class FinOpsController {

    private final FinOpsService finOpsService;
    private final WhatIfSimulatorService whatIfSimulatorService;

    @GetMapping("/overview")
    @Operation(summary = "Get FinOps overview, total spend, waste, savings, and team breakdown")
    public ResponseEntity<FinOpsOverviewDto> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Environment environment) {
        return ResponseEntity.ok(finOpsService.getOverview(from, to, environment));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "List namespace-level rightsizing recommendations and wasted spend")
    public ResponseEntity<List<FinOpsNamespaceRecommendationDto>> getRecommendations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Environment environment,
            @RequestParam(required = false) FinOpsEfficiencyRating rating,
            @RequestParam(required = false) String teamName,
            @RequestParam(required = false) BigDecimal minWaste) {
        return ResponseEntity.ok(finOpsService.getRecommendations(from, to, environment, rating, teamName, minWaste));
    }

    @GetMapping("/pricing")
    @Operation(summary = "Get active FinOps unit pricing rates")
    public ResponseEntity<FinOpsPricingConfigDto> getPricing() {
        return ResponseEntity.ok(finOpsService.getPricingConfig());
    }

    @PutMapping("/pricing")
    @Operation(summary = "Update FinOps unit pricing rates (Admin only)")
    public ResponseEntity<FinOpsPricingConfigDto> updatePricing(@RequestBody FinOpsPricingConfigDto pricingConfig) {
        return ResponseEntity.ok(finOpsService.updatePricingConfig(pricingConfig));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export FinOps rightsizing report as CSV")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Environment environment) {
        byte[] csv = finOpsService.exportCsv(from, to, environment);
        String filename = String.format("openshift-finops-rightsizing-%s.csv", LocalDate.now());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping("/simulator/simulate")
    @Operation(summary = "Run interactive what-if capacity and cost simulation")
    public ResponseEntity<WhatIfSimulationResultDto> simulate(@RequestBody(required = false) WhatIfSimulationRequestDto request) {
        return ResponseEntity.ok(whatIfSimulatorService.simulate(request));
    }

    @GetMapping("/simulator/presets")
    @Operation(summary = "Get predefined enterprise what-if simulation scenarios")
    public ResponseEntity<List<WhatIfPresetDto>> getPresets() {
        return ResponseEntity.ok(whatIfSimulatorService.getPresets());
    }
}
