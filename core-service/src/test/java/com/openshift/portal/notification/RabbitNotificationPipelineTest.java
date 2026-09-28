package com.openshift.portal.notification;

import com.openshift.portal.config.RabbitMqConfig;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.test.TestRabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * With RabbitMQ enabled, jobs and emails go through the two queues: messages are converted to JSON and back, and
 * reach the listeners. {@link TestRabbitTemplate} delivers to the listeners directly, so no broker is needed.
 */
@SpringJUnitConfig(RabbitNotificationPipelineTest.Config.class)
@TestPropertySource(properties = "openshift.portal.rabbitmq.enabled=true")
class RabbitNotificationPipelineTest {

    @Autowired
    private NotificationQueue queue;
    @Autowired
    private TestRabbitTemplate rabbitTemplate;
    @MockBean
    private ReportDeliveryService reportDelivery;
    @MockBean
    private MailDeliveryService mailDelivery;

    @Test
    void reportJobsReachTheGenerationListener() {
        assertThat(queue).isInstanceOf(RabbitNotificationQueue.class);
        ReportJob job = new ReportJob(UUID.randomUUID(), true);

        queue.submitReport(job);

        verify(reportDelivery).generate(job);
    }

    @Test
    void emailsWithTheirAttachmentReachTheEmailListener() {
        byte[] pdf = "%PDF-1.4 fake".getBytes();
        queue.submitMail(new OutgoingMail(7L, List.of("ops@example.com", "finops@example.com"), "Weekly audit",
                "<p>Hi</p>", "audit.pdf", "application/pdf", pdf));

        ArgumentCaptor<OutgoingMail> received = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailDelivery).send(received.capture());
        OutgoingMail mail = received.getValue();
        assertThat(mail.notificationId()).isEqualTo(7L);
        assertThat(mail.to()).containsExactly("ops@example.com", "finops@example.com");
        assertThat(mail.subject()).isEqualTo("Weekly audit");
        assertThat(mail.attachmentName()).isEqualTo("audit.pdf");
        assertThat(mail.attachment()).isEqualTo(pdf);
    }

    @Test
    void messagesAreJsonWithoutJavaTypeRequirements() {
        Message message = rabbitTemplate.getMessageConverter().toMessage(new ReportJob(UUID.randomUUID(), false),
                new org.springframework.amqp.core.MessageProperties());

        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(new String(message.getBody())).contains("\"reportId\"", "\"manual\":false");
    }

    @Test
    void aFailingStepIsLoggedAndNotRedelivered() {
        ReportJob job = new ReportJob(UUID.randomUUID(), false);
        doThrow(new IllegalStateException("database down")).when(reportDelivery).generate(job);

        // The listener swallows it: rethrowing would requeue the job and could email recipients twice
        queue.submitReport(job);

        verify(reportDelivery).generate(job);
    }

    @Configuration
    @EnableRabbit
    @Import({RabbitMqConfig.class, RabbitNotificationQueue.class, RabbitNotificationListeners.class})
    static class Config {

        @Bean
        ConnectionFactory connectionFactory() throws Exception {
            ConnectionFactory factory = mock(ConnectionFactory.class);
            Connection connection = mock(Connection.class);
            Channel channel = mock(Channel.class);
            willReturn(connection).given(factory).createConnection();
            willReturn(channel).given(connection).createChannel(anyBoolean());
            given(channel.isOpen()).willReturn(true);
            return factory;
        }

        @Bean
        TestRabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
            TestRabbitTemplate template = new TestRabbitTemplate(connectionFactory);
            template.setMessageConverter(jsonMessageConverter);
            return template;
        }

        @Bean
        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                           MessageConverter jsonMessageConverter) {
            SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
            factory.setConnectionFactory(connectionFactory);
            factory.setMessageConverter(jsonMessageConverter);
            return factory;
        }
    }
}
