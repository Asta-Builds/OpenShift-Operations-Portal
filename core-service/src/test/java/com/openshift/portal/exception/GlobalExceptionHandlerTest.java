package com.openshift.portal.exception;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.config.SecurityConfig;
import com.openshift.portal.controller.ReportController;
import com.openshift.portal.notification.ReportScheduleService;
import com.openshift.portal.repository.SavedReportRepository;
import com.openshift.portal.service.PdfReportGeneratorService;
import com.openshift.portal.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, AcmProperties.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportingService reportingService;

    @MockBean
    private PdfReportGeneratorService pdfReportGenerator;

    @MockBean
    private SavedReportRepository savedReportRepository;

    @MockBean
    private ReportScheduleService scheduleService;

    @Test
    void invalidParameterValue_returns400WithoutClassNames() throws Exception {
        mockMvc.perform(get("/reports/export").param("type", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("'type'")))
                .andExpect(jsonPath("$.message").value(not(containsString("com.openshift"))));
    }

    @Test
    void unknownPath_returns404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void accessDenied_returns403() throws Exception {
        when(scheduleService.list()).thenThrow(new AccessDeniedException("denied"));

        mockMvc.perform(get("/reports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unexpectedError_returns500WithoutInternalDetails() throws Exception {
        when(scheduleService.list()).thenThrow(new IllegalStateException("jdbc:postgresql://db:5432 password=secret"));

        mockMvc.perform(get("/reports"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }
}
