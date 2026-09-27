package com.openshift.portal.controller;

import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.dto.AttributionReportDto;
import com.openshift.portal.service.AttributionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/attribution")
@RequiredArgsConstructor
@Tag(name = "Attribution", description = "Cost center and team capacity attribution analysis")
public class AttributionController {

    /** Period used when none is given: the last 30 days, today included. */
    static final int DEFAULT_DAYS = 30;

    private final AttributionService attributionService;

    /** Requests and usage by team and cost center; {@code from} and {@code to} are inclusive days. */
    @GetMapping("/teams")
    public ResponseEntity<AttributionReportDto> byTeam(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Environment environment) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        return ResponseEntity.ok(attributionService.attribute(start, end, environment));
    }
}
