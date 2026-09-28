package com.openshift.portal.dto;

import com.openshift.portal.domain.entity.Notification;
import com.openshift.portal.domain.enums.NotificationKind;
import com.openshift.portal.domain.enums.NotificationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** One email the portal sent or tried to send. */
public record NotificationDto(
        Long id,
        NotificationKind kind,
        UUID reportId,
        String subject,
        String recipients,
        NotificationStatus status,
        String detail,
        LocalDateTime createdAt,
        LocalDateTime sentAt) {

    public static NotificationDto of(Notification n) {
        return new NotificationDto(n.getId(), n.getKind(), n.getReportId(), n.getSubject(), n.getRecipients(),
                n.getStatus(), n.getDetail(), n.getCreatedAt(), n.getSentAt());
    }
}
