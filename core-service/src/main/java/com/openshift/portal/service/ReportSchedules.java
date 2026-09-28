package com.openshift.portal.service;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parsing and validation of report schedules and recipient lists. */
public final class ReportSchedules {

    static final int MAX_RECIPIENTS = 50;

    private static final Pattern SEPARATORS = Pattern.compile("[,;\\s]+");

    private ReportSchedules() {
    }

    /**
     * Accepts Unix five-field cron (minute first, e.g. {@code 0 7 * * MON}) or Spring's six-field form (second first)
     * and returns the six-field form.
     *
     * @throws IllegalArgumentException when it is neither
     */
    public static String normalizeCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("a cron schedule is required");
        }
        String trimmed = cron.trim().replaceAll("\\s+", " ");
        String sixFields = trimmed.split(" ").length == 5 ? "0 " + trimmed : trimmed;
        if (!CronExpression.isValidExpression(sixFields)) {
            throw new IllegalArgumentException("'" + cron + "' is not a valid cron schedule; use five fields, e.g. '0 7 * * MON'");
        }
        return sixFields;
    }

    /** First time after {@code after} the schedule fires, or null when it never does again. */
    public static LocalDateTime nextRun(String cron, LocalDateTime after) {
        return CronExpression.parse(normalizeCron(cron)).next(after);
    }

    /**
     * Addresses separated by commas, semicolons or spaces, as a list.
     *
     * @throws IllegalArgumentException when there are none, too many, or one is not an email address
     */
    public static List<String> parseRecipients(String recipients) {
        List<String> addresses = new ArrayList<>();
        if (recipients != null) {
            for (String candidate : SEPARATORS.split(recipients.trim())) {
                if (candidate.isEmpty()) {
                    continue;
                }
                if (!candidate.contains("@")) {
                    throw new IllegalArgumentException("'" + candidate + "' is not an email address");
                }
                try {
                    new InternetAddress(candidate, true).validate();
                } catch (AddressException e) {
                    throw new IllegalArgumentException("'" + candidate + "' is not an email address");
                }
                if (!addresses.contains(candidate)) {
                    addresses.add(candidate);
                }
            }
        }
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("at least one recipient is required");
        }
        if (addresses.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException("at most " + MAX_RECIPIENTS + " recipients are allowed");
        }
        return addresses;
    }
}
