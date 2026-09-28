package com.openshift.portal.notification;

import com.openshift.portal.config.AcmProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends one prepared email over SMTP and records the outcome. Spring configures the SMTP server from
 * {@code spring.mail.host}; without it nothing is sent and the notification says so.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailDeliveryService {

    static final String NO_SMTP = "No SMTP server is configured (spring.mail.host), so nothing was sent";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final NotificationService notifications;
    private final AcmProperties properties;

    public void send(OutgoingMail mail) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Email '{}' to {} not sent: no SMTP server is configured", mail.subject(), mail.to());
            notifications.markNotSent(mail.notificationId(), NO_SMTP);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, mail.attachment() != null, "UTF-8");
            helper.setFrom(properties.getNotifications().getFrom());
            helper.setTo(mail.to().toArray(String[]::new));
            helper.setSubject(mail.subject());
            helper.setText(mail.html(), true);
            if (mail.attachment() != null) {
                helper.addAttachment(mail.attachmentName(), new ByteArrayResource(mail.attachment()),
                        mail.attachmentContentType());
            }
            sender.send(message);
            notifications.markSent(mail.notificationId());
            log.info("Email '{}' sent to {}", mail.subject(), mail.to());
        } catch (Exception e) {
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Email '{}' to {} failed: {}", mail.subject(), mail.to(), error);
            notifications.markFailed(mail.notificationId(), error);
        }
    }
}
