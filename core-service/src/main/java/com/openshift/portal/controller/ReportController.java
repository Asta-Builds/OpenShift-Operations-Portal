package com.openshift.portal.controller;

import com.openshift.portal.domain.entity.ReportDefinition;
import com.openshift.portal.domain.entity.SavedReport;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.repository.SavedReportRepository;
import com.openshift.portal.service.PdfReportGeneratorService;
import com.openshift.portal.service.ReportingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportingService reportingService;
    private final PdfReportGeneratorService pdfReportGenerator;
    private final SavedReportRepository savedReportRepository;

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

    @GetMapping
    public ResponseEntity<List<ReportDefinition>> listReports() {
        return ResponseEntity.ok(reportingService.listReports());
    }

    @PostMapping
    public ResponseEntity<ReportDefinition> createReport(@RequestBody CreateReportRequest request) {
        ReportDefinition report = reportingService.createReportSchedule(
                request.getTitle(),
                request.getReportType(),
                request.getCronSchedule(),
                request.getRecipients()
        );
        return ResponseEntity.ok(report);
    }

    @GetMapping("/saved")
    public ResponseEntity<List<SavedReport>> listSavedReports(@RequestParam(defaultValue = "admin") String userId) {
        return ResponseEntity.ok(savedReportRepository.findByUserId(userId));
    }

    @PostMapping("/saved")
    public ResponseEntity<SavedReport> saveReportConfig(@RequestBody SaveReportConfigRequest request) {
        SavedReport saved = SavedReport.builder()
                .title(request.getTitle())
                .userId(request.getUserId() != null ? request.getUserId() : "admin")
                .reportType(request.getReportType())
                .parametersJson(request.getParametersJson())
                .build();
        return ResponseEntity.ok(savedReportRepository.save(saved));
    }

    @Data
    public static class CreateReportRequest {
        private String title;
        private ReportType reportType;
        private String cronSchedule;
        private String recipients;
    }

    @Data
    public static class SaveReportConfigRequest {
        private String title;
        private String userId;
        private ReportType reportType;
        private String parametersJson;
    }
}
