package com.openshift.portal.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "license_watermarks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenseWatermark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watermark_date", nullable = false, unique = true)
    private LocalDate watermarkDate;

    @Column(name = "peak_worker_cores", nullable = false)
    private Integer peakWorkerCores;

    @Column(name = "licensed_cap_cores", nullable = false)
    private Integer licensedCapCores;

    @Column(name = "compliance_breach")
    @Builder.Default
    private Boolean complianceBreach = false;

    @Column(name = "recorded_at", updatable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();
}
