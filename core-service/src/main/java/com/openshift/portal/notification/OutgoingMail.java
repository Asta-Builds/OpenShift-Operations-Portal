package com.openshift.portal.notification;

import java.util.List;

/**
 * One email ready to send; queued on {@code report.email.queue} when RabbitMQ is enabled. The attachment fields are
 * null for alerts.
 *
 * @param notificationId the notification row that records the outcome
 */
public record OutgoingMail(
        long notificationId,
        List<String> to,
        String subject,
        String html,
        String attachmentName,
        String attachmentContentType,
        byte[] attachment) {
}
