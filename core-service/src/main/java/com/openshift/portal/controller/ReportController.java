package com.openshift.portal.controller;

import com.openshift.portal.domain.entity.SavedReport;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.dto.ReportScheduleDto;
import com.openshift.portal.dto.ReportScheduleRequests;
import com.openshift.portal.notification.ReportScheduleService;
import com.openshift.portal.repository.SavedReportRepository;
import com.openshift.portal.service.PdfReportGeneratorService;
import com.openshift.portal.service.ReportingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "CSV/PDF export, saved report presets, and report schedules emailed to their recipients")
public class ReportController {

    private final ReportingService reportingService;
    private final PdfReportGeneratorService pdfReportGenerator;
    private final SavedReportRepository savedReportRepository;
    private final ReportScheduleService scheduleService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsvReport(@RequestParam(defaultValue = "FLEET_CAPACITY") ReportType type) {
        byte[] csvData = reportingService.generateCsvReport(type);
        String filename = String.format("openshift-%s-%s.csv",
                type.name().toLowerCase(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdfReport(@RequestParam(defaultValue = "FLEET_CAPACITY") ReportType type) {
        byte[] pdfData = pdfReportGenerator.generatePdfReport(type);
        String filename = String.format("openshift-%s-%s.pdf",
                type.name().toLowerCase(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfData);
    }

    /** Report schedules with their next run and latest delivery. */
    @GetMapping
    public ResponseEntity<List<ReportScheduleDto>> listSchedules() {
        return ResponseEntity.ok(scheduleService.list());
    }

    @PostMapping
    public ResponseEntity<ReportScheduleDto> createSchedule(@Valid @RequestBody ReportScheduleRequests.Create request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ReportScheduleDto> updateSchedule(@PathVariable UUID id,
                                                            @Valid @RequestBody ReportScheduleRequests.Update request) {
        return ResponseEntity.ok(scheduleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Sends the report to its recipients now; the outcome appears in the schedule's last delivery. */
    @PostMapping("/{id}/run")
    public ResponseEntity<Map<String, String>> runSchedule(@PathVariable UUID id) {
        scheduleService.runNow(id);
        return ResponseEntity.accepted().body(Map.of("message", "The report was started; see its last delivery for the outcome."));
    }

    @GetMapping("/saved")
    public ResponseEntity<List<SavedReport>> listSavedReports(Authentication authentication) {
        return ResponseEntity.ok(savedReportRepository.findByUserId(ownerOf(authentication)));
    }

    @PostMapping("/saved")
    public ResponseEntity<SavedReport> saveReportConfig(@RequestBody SaveReportConfigRequest request,
                                                        Authentication authentication) {
        SavedReport saved = SavedReport.builder()
                .title(request.getTitle())
                .userId(ownerOf(authentication))
                .reportType(request.getReportType())
                .parametersJson(request.getParametersJson())
                .build();
        return ResponseEntity.ok(savedReportRepository.save(saved));
    }

    /** Saved reports belong to the token's subject; with security disabled every caller shares one owner. */
    private static String ownerOf(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : "anonymous";
    }

    @Data
    public static class SaveReportConfigRequest {
        private String title;
        private ReportType reportType;
        private String parametersJson;
    }
}
