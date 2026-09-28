package com.openshift.portal.notification;

import com.openshift.portal.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes the two queues. Both steps record their own failures, so a message is never rejected: a redelivered report
 * would email its recipients twice.
 */
@Component
@ConditionalOnProperty(name = "openshift.portal.rabbitmq.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RabbitNotificationListeners {

    private final ReportDeliveryService reportDelivery;
    private final MailDeliveryService mailDelivery;

    @RabbitListener(queues = RabbitMqConfig.REPORT_GENERATION_QUEUE)
    public void onReportJob(ReportJob job) {
        try {
            reportDelivery.generate(job);
        } catch (RuntimeException e) {
            log.error("Report job {} failed", job, e);
        }
    }

    @RabbitListener(queues = RabbitMqConfig.REPORT_EMAIL_QUEUE)
    public void onMail(OutgoingMail mail) {
        try {
            mailDelivery.send(mail);
        } catch (RuntimeException e) {
            log.error("Email of notification {} failed", mail.notificationId(), e);
        }
    }
}
