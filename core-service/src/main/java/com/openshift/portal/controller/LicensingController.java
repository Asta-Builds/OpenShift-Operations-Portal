package com.openshift.portal.controller;

import com.openshift.portal.dto.LicenseAuditDto;
import com.openshift.portal.service.LicensingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/licensing")
@RequiredArgsConstructor
@Tag(name = "Licensing", description = "OpenShift core license compliance, socket allocations, and watermark audits")
public class LicensingController {

    private final LicensingService licensingService;

    @GetMapping("/audit")
    public ResponseEntity<LicenseAuditDto> getLicenseAudit() {
        LicenseAuditDto audit = licensingService.generateLicenseAudit();
        return ResponseEntity.ok(audit);
    }
}
