package com.openshift.portal.notification;

import com.openshift.portal.config.AcmProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailDeliveryServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private NotificationService notifications;

    private final OutgoingMail mail = new OutgoingMail(42L, List.of("ops@example.com"), "Subject", "<p>Body</p>",
            "report.csv", "text/csv", "a,b\n".getBytes());

    @Test
    void withoutAnSmtpServerTheEmailIsRecordedAsNotSent() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        service().send(mail);

        verify(notifications).markNotSent(42L, MailDeliveryService.NO_SMTP);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void smtpFailureIsRecordedAsFailedAndNotThrown() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("Connection refused: smtp.example.com:25")).when(mailSender).send(any(MimeMessage.class));

        service().send(mail);

        verify(notifications).markFailed(42L, "Connection refused: smtp.example.com:25");
        verify(notifications, never()).markSent(anyLong());
    }

    private MailDeliveryService service() {
        return new MailDeliveryService(mailSenderProvider, notifications, new AcmProperties());
    }
}
