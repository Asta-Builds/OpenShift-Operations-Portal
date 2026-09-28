package com.openshift.portal.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs each step on the calling thread; used when RabbitMQ is disabled (development and tests). */
@Component
@ConditionalOnProperty(name = "openshift.portal.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class DirectNotificationQueue implements NotificationQueue {

    // Providers, as both services submit work back to this queue
    private final ObjectProvider<ReportDeliveryService> reportDelivery;
    private final ObjectProvider<MailDeliveryService> mailDelivery;

    @Override
    public void submitReport(ReportJob job) {
        reportDelivery.getObject().generate(job);
    }

    @Override
    public void submitMail(OutgoingMail mail) {
        mailDelivery.getObject().send(mail);
    }
}
