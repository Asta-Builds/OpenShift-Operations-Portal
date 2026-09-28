package com.openshift.portal.notification;

import java.util.UUID;

/**
 * Asks for one run of a report schedule; queued on {@code report.generation.queue} when RabbitMQ is enabled.
 *
 * @param manual true for "send now", which also runs a disabled schedule and leaves its timing alone
 */
public record ReportJob(UUID reportId, boolean manual) {
}
