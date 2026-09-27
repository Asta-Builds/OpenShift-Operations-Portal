package com.openshift.portal.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final SpringTemplateEngine templateEngine;

    public void sendReportSummaryEmail(String recipient, String reportTitle, byte[] attachment, String filename) {
        log.info("Preparing automated dispatch of report '{}' to recipient '{}'", reportTitle, recipient);

        if (mailSender == null) {
            log.warn("JavaMailSender is not configured in this profile. Simulated dispatch to: {}", recipient);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(recipient);
            helper.setSubject("[OpenShift Operations Portal] " + reportTitle);

            Context context = new Context();
            context.setVariables(Map.of(
                    "title", reportTitle,
                    "recipient", recipient
            ));
            String htmlContent = templateEngine.process("email/report-summary", context);
            helper.setText(htmlContent, true);

            if (attachment != null && filename != null) {
                helper.addAttachment(filename, new ByteArrayResource(attachment));
            }

            mailSender.send(message);
            log.info("Report email successfully dispatched to {}", recipient);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", recipient, e.getMessage());
        }
    }

    public void sendLicenseBreachAlert(String recipient, int currentCores, int licensedCap) {
        log.warn("LICENSE COMPLIANCE ALERT: Current cores ({}) exceeded enterprise cap ({})! Notifying {}",
                currentCores, licensedCap, recipient);

        if (mailSender == null) {
            log.info("Simulated license breach notification sent to: {}", recipient);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(recipient);
            helper.setSubject("[CRITICAL ALERT] OpenShift License Core Watermark Breach");
            helper.setText(String.format(
                    "CRITICAL NOTICE: Active OpenShift worker cores (%d) have exceeded your contracted license cap (%d). " +
                    "Please review the licensing audit page in the OpenShift Operations Portal to avoid non-compliance penalties.",
                    currentCores, licensedCap
            ));

            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to dispatch license breach alert: {}", e.getMessage());
        }
    }
}
