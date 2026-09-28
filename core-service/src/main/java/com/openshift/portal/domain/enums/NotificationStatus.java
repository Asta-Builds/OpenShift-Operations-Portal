package com.openshift.portal.domain.enums;

public enum NotificationStatus {
    /** Handed to the email step (a RabbitMQ queue, or the same thread without RabbitMQ). */
    QUEUED,
    SENT,
    /** No SMTP server is configured, so nothing was sent. */
    NOT_SENT,
    FAILED
}
