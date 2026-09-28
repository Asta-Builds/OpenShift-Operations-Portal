package com.openshift.portal;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import com.openshift.portal.domain.entity.Notification;
import com.openshift.portal.domain.enums.NotificationKind;
import com.openshift.portal.domain.enums.NotificationStatus;
import com.openshift.portal.dto.ForecastingProjectionDto;
import com.openshift.portal.dto.LicenseAuditDto;
import com.openshift.portal.notification.ReportScheduler;
import com.openshift.portal.repository.NotificationRepository;
import com.openshift.portal.repository.ReportDefinitionRepository;
import com.openshift.portal.service.AcmCollectorService;
import com.openshift.portal.service.ForecastingService;
import com.openshift.portal.service.LicensingService;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Report schedules and alerts against the simulated fleet, delivered to an embedded SMTP server, without RabbitMQ
 * (each step on the calling thread).
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "openshift.portal.notifications.from=portal@portal.test",
        "openshift.portal.notifications.alert-recipients=ops@portal.test, oncall@portal.test",
        "openshift.portal.notifications.portal-url=https://portal.example.com"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ReportDeliveryIntegrationTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ReportScheduler scheduler;
    @Autowired
    private AcmCollectorService collectorService;
    @Autowired
    private LicensingService licensingService;
    @Autowired
    private ForecastingService forecastingService;
    @Autowired
    private ReportDefinitionRepository reportRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        reportRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void scheduleSentNowArrivesWithItsPdfAndSummary() throws Exception {
        String id = create("""
                {"title":"Weekly license audit","reportType":"LICENSE_AUDIT","format":"PDF",
                 "cronSchedule":"0 7 * * MON","recipients":"finops@portal.test; cio@portal.test"}
                """);

        mockMvc.perform(post("/reports/{id}/run", id)).andExpect(status().isAccepted());

        assertThat(smtp.waitForIncomingEmail(5000, 2)).isTrue();
        MimeMessage message = smtp.getReceivedMessages()[0];
        assertThat(message.getSubject()).isEqualTo("[OpenShift Operations Portal] Weekly license audit");
        assertThat(message.getFrom()[0].toString()).isEqualTo("portal@portal.test");
        assertThat(Arrays.stream(smtp.getReceivedMessages()).flatMap(m -> recipients(m).stream()))
                .containsExactlyInAnyOrder("finops@portal.test", "cio@portal.test", "finops@portal.test", "cio@portal.test");
        Map<String, byte[]> parts = parts(message);
        String html = new String(parts.get("html"), StandardCharsets.UTF_8);
        assertThat(html).contains("Weekly license audit", "Compliance status:", "High watermark:",
                "https://portal.example.com/reports");
        String attachment = parts.keySet().stream().filter(name -> name.endsWith(".pdf")).findFirst().orElseThrow();
        assertThat(attachment).startsWith("openshift-license_audit-");
        assertThat(new String(parts.get(attachment), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        mockMvc.perform(get("/reports"))
                .andExpect(jsonPath("$[0].lastDelivery.status").value("SENT"))
                .andExpect(jsonPath("$[0].lastRunAt").doesNotExist());
        mockMvc.perform(get("/notifications"))
                .andExpect(jsonPath("$[0].kind").value("SCHEDULED_REPORT"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].reportId").value(id))
                .andExpect(jsonPath("$[0].recipients").value("finops@portal.test, cio@portal.test"));
    }

    @Test
    void dueScheduleIsEmailedByTheSchedulerUntilDisabled() throws Exception {
        String id = create("""
                {"title":"Fleet capacity every minute","reportType":"FLEET_CAPACITY","format":"CSV",
                 "cronSchedule":"* * * * *","recipients":"capacity@portal.test"}
                """);

        scheduler.runDue(LocalDateTime.now().plusMinutes(2));

        assertThat(smtp.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage message = mine(smtp.getReceivedMessages(), "Fleet capacity every minute").get(0);
        Map<String, byte[]> parts = parts(message);
        String csvName = parts.keySet().stream().filter(name -> name.endsWith(".csv")).findFirst().orElseThrow();
        assertThat(new String(parts.get(csvName), StandardCharsets.UTF_8)).startsWith("\"Cluster Name\"");
        assertThat(new String(parts.get("html"), StandardCharsets.UTF_8)).contains("Clusters:", "CPU requested:");
        mockMvc.perform(get("/reports"))
                .andExpect(jsonPath("$[0].lastRunAt").exists())
                .andExpect(jsonPath("$[0].nextRunAt").exists());

        mockMvc.perform(patch("/reports/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.nextRunAt").doesNotExist());
        scheduler.runDue(LocalDateTime.now().plusMinutes(10));
        Thread.sleep(500);
        assertThat(mine(smtp.getReceivedMessages(), "Fleet capacity every minute")).hasSize(1);
    }

    @Test
    void invalidSchedulesAreRejected() throws Exception {
        rejected("""
                {"title":"Bad cron","reportType":"FLEET_CAPACITY","cronSchedule":"every monday","recipients":"a@portal.test"}
                """, "cronSchedule");
        rejected("""
                {"title":"Bad recipient","reportType":"FLEET_CAPACITY","cronSchedule":"0 7 * * MON","recipients":"a@portal.test, nobody"}
                """, "'nobody'");
        rejected("""
                {"title":"No export","reportType":"GROWTH_FORECAST","cronSchedule":"0 7 * * MON","recipients":"a@portal.test"}
                """, "cannot be exported");
        rejected("""
                {"reportType":"FLEET_CAPACITY","cronSchedule":"0 7 * * MON","recipients":"a@portal.test"}
                """, "title");
        assertThat(reportRepository.count()).isZero();
    }

    @Test
    void collectionsEmailLicenseAndRunwayAlertsOnceADay() throws Exception {
        // Facts about the simulated fleet this test relies on
        LicenseAuditDto audit = licensingService.generateLicenseAudit();
        assertThat(audit.getComplianceStatus()).isEqualTo(LicenseAuditDto.ComplianceStatus.BREACH);
        ForecastingProjectionDto projection = forecastingService.generateProjection(30, null);
        assertThat(projection.getRunwayDaysCores()).isNotNull().isLessThanOrEqualTo(60);

        collectorService.triggerCollection();

        assertThat(smtp.waitForIncomingEmail(5000, 4)).isTrue(); // two alerts, two recipients each
        List<String> subjects = Arrays.stream(smtp.getReceivedMessages()).map(ReportDeliveryIntegrationTest::subject).distinct().toList();
        assertThat(subjects).anyMatch(s -> s.startsWith("[OpenShift Operations Portal] License cap exceeded: "));
        assertThat(subjects).anyMatch(s -> s.startsWith("[OpenShift Operations Portal] Capacity runway: "));
        String licenseHtml = new String(parts(mine(smtp.getReceivedMessages(), "License cap exceeded").get(0)).get("html"),
                StandardCharsets.UTF_8);
        assertThat(licenseHtml).contains("contracted cap is " + audit.getLicensedCapCores() + " cores",
                "https://portal.example.com/licensing");

        collectorService.triggerCollection();
        Thread.sleep(500);
        assertThat(smtp.getReceivedMessages()).hasSize(4);
        List<Notification> alerts = notificationRepository.findAll();
        assertThat(alerts).extracting(Notification::getKind)
                .containsExactlyInAnyOrder(NotificationKind.LICENSE_BREACH, NotificationKind.CAPACITY_RUNWAY);
        assertThat(alerts).extracting(Notification::getStatus).containsOnly(NotificationStatus.SENT);
        assertThat(alerts).extracting(Notification::getDedupeKey).allMatch(key -> key.endsWith(":" + LocalDate.now()));
    }

    private String create(String body) throws Exception {
        String created = mockMvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.nextRunAt").exists())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private void rejected(String body, String messagePart) throws Exception {
        mockMvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(messagePart)));
    }

    private static List<MimeMessage> mine(MimeMessage[] messages, String subjectPart) {
        return Arrays.stream(messages).filter(m -> subject(m).contains(subjectPart)).toList();
    }

    private static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> recipients(MimeMessage message) {
        try {
            return Arrays.stream(message.getAllRecipients()).map(Object::toString).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The HTML body under "html" and each attachment under its file name. */
    private static Map<String, byte[]> parts(Part part) throws Exception {
        Map<String, byte[]> found = new HashMap<>();
        collect(part, found);
        return found;
    }

    private static void collect(Part part, Map<String, byte[]> found) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, found);
            }
        } else if (part.getFileName() != null) {
            found.put(part.getFileName(), read(part.getInputStream()));
        } else if (part.isMimeType("text/html")) {
            found.put("html", ((String) part.getContent()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] read(InputStream in) throws Exception {
        try (in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }
}
