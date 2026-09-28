package com.openshift.portal.domain.entity;

import com.openshift.portal.domain.enums.NotificationKind;
import com.openshift.portal.domain.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** One email the portal sent or tried to send: a scheduled report or an alert, with its outcome. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationKind kind;

    /** The schedule of a SCHEDULED_REPORT; null for alerts, and once the schedule is deleted. */
    @Column(name = "report_id")
    private UUID reportId;

    @Column(nullable = false)
    private String subject;

    /** Comma-separated addresses. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String recipients;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(columnDefinition = "TEXT")
    private String detail;

    /** Alerts only: one notification per kind and day. */
    @Column(name = "dedupe_key", unique = true)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
