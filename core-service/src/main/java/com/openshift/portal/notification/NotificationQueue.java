package com.openshift.portal.notification;

/**
 * Hands work to the next step. With RabbitMQ the steps run on its queues, so report generation and email dispatch
 * are decoupled and survive a restart; without it they run on the calling thread.
 */
public interface NotificationQueue {

    void submitReport(ReportJob job);

    void submitMail(OutgoingMail mail);
}
