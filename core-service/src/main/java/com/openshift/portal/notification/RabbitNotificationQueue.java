package com.openshift.portal.notification;

import com.openshift.portal.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publishes each step to its durable RabbitMQ queue; {@link RabbitNotificationListeners} consumes them. */
@Component
@ConditionalOnProperty(name = "openshift.portal.rabbitmq.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RabbitNotificationQueue implements NotificationQueue {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void submitReport(ReportJob job) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.REPORT_GENERATION_QUEUE, job);
    }

    @Override
    public void submitMail(OutgoingMail mail) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.REPORT_EMAIL_QUEUE, mail);
    }
}
