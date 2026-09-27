package com.openshift.portal.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "openshift.portal.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class RabbitMqConfig {

    public static final String REPORT_GENERATION_QUEUE = "report.generation.queue";
    public static final String REPORT_EMAIL_QUEUE = "report.email.queue";

    @Bean
    public Queue reportGenerationQueue() {
        return new Queue(REPORT_GENERATION_QUEUE, true);
    }

    @Bean
    public Queue reportEmailQueue() {
        return new Queue(REPORT_EMAIL_QUEUE, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
