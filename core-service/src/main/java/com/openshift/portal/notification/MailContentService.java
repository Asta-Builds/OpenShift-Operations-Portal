package com.openshift.portal.notification;

import com.openshift.portal.config.AcmProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Renders email bodies from the templates in {@code templates/email}. */
@Service
@RequiredArgsConstructor
public class MailContentService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SpringTemplateEngine templateEngine;
    private final AcmProperties properties;

    public String report(String title, String reportTypeLabel, String attachmentName, List<String> summary) {
        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("reportTypeLabel", reportTypeLabel);
        context.setVariable("attachmentName", attachmentName);
        context.setVariable("summary", summary);
        context.setVariable("generatedAt", LocalDateTime.now().format(TIMESTAMP));
        context.setVariable("portalLink", link("/reports"));
        return templateEngine.process("email/report-summary", context);
    }

    /** @param portalPath the UI page with the details, such as {@code /licensing} */
    public String alert(String heading, List<String> lines, String portalPath) {
        Context context = new Context();
        context.setVariable("heading", heading);
        context.setVariable("lines", lines);
        context.setVariable("generatedAt", LocalDateTime.now().format(TIMESTAMP));
        context.setVariable("portalLink", link(portalPath));
        return templateEngine.process("email/alert", context);
    }

    private String link(String path) {
        String base = properties.getNotifications().getPortalUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }
}
