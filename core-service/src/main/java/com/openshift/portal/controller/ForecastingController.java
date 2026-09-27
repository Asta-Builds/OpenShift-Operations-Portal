package com.openshift.portal.controller;

import com.openshift.portal.dto.ForecastingProjectionDto;
import com.openshift.portal.service.ForecastingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/forecasting")
@RequiredArgsConstructor
@Tag(name = "Capacity Forecasting", description = "Historical utilization trends and capacity exhaustion projection engine")
public class ForecastingController {

    private final ForecastingService forecastingService;

    @GetMapping("/projection")
    public ResponseEntity<ForecastingProjectionDto> getProjection(
            @RequestParam(defaultValue = "30") int horizonDays,
            @RequestParam(required = false) UUID clusterId) {
        ForecastingProjectionDto projection = forecastingService.generateProjection(horizonDays, clusterId);
        return ResponseEntity.ok(projection);
    }
}
