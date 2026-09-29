package com.openshift.portal;

import com.jayway.jsonpath.JsonPath;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.service.AcmCollectorService;
import com.openshift.portal.service.AcmSimulatorService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The hub page's API: collecting one hub, its history, connection tests and the shared collection lock. */
@SpringBootTest(properties = "resilience4j.retry.configs.acmHub.wait-duration=10ms")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class HubManagementIntegrationTest {

    private static final String HUB = "acm-hub-primary-eu";
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AcmHubRepository acmHubRepository;
    @Autowired
    private ClusterRepository clusterRepository;
    @Autowired
    private AcmSimulatorService simulatorService;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private LockProvider lockProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AcmHub hub;

    @BeforeEach
    void findHub() {
        // Expired rather than deleted: ShedLock only updates lock rows it has created before
        jdbcTemplate.update("UPDATE shedlock SET lock_until = TIMESTAMP '2000-01-01 00:00:00' WHERE name = ?",
                AcmCollectorService.COLLECTION_LOCK);
        hub = acmHubRepository.findByName(HUB).orElseThrow();
    }

    @AfterEach
    void clearFaults() {
        simulatorService.clearFaults();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void collectsOneHubAndListsItsHistoryNewestFirst() throws Exception {
        long clusters = clusterRepository.countByAcmHubId(hub.getId());
        assertThat(clusters).isPositive();

        String first = mockMvc.perform(post("/hubs/{id}/collect", hub.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.clustersOk").value(clusters))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/hubs/{id}/collect", hub.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long firstId = ((Number) JsonPath.read(first, "$.id")).longValue();
        long secondId = ((Number) JsonPath.read(second, "$.id")).longValue();

        mockMvc.perform(get("/hubs/{id}/sync-runs", hub.getId()).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[1].id").value(firstId));

        mockMvc.perform(get("/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + HUB + "')].clusterCount").value(contains((int) clusters)))
                .andExpect(jsonPath("$[?(@.name == '" + HUB + "')].latestSyncRun.id").value(contains((int) secondId)))
                // Simulated hubs read no Secret, so whether one is mounted is not reported
                .andExpect(jsonPath("$[0].credentialsMounted").value(nullValue()));
    }

    @Test
    void connectionTestReportsASimulatedOutage() throws Exception {
        mockMvc.perform(post("/hubs/{id}/test", hub.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.checks[?(@.target == 'API')].status").value(contains("OK")));

        simulatorService.setHubOutage(HUB, true);

        mockMvc.perform(post("/hubs/{id}/test", hub.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.checks[?(@.target == 'API')].status").value(contains("FAILED")))
                .andExpect(jsonPath("$.checks[?(@.target == 'API')].message")
                        .value(contains("Simulated outage of ACM Hub: " + hub.getApiUrl())));
        mockMvc.perform(post("/hubs/{id}/collect", hub.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value(containsString("Simulated outage")));
    }

    @Test
    void testsSettingsBeforeTheyAreRegistered() throws Exception {
        String settings = "{\"name\":\"hub-new\",\"apiUrl\":\"https://api.hub-new.example.com:6443\","
                + "\"credentialsSecretRef\":\"hub-new-credentials\",\"searchUrl\":\"https://search.example.com/graphql\"}";

        mockMvc.perform(post("/hubs/test").contentType(MediaType.APPLICATION_JSON).content(settings))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.checks[*].target").value(contains("CREDENTIALS", "API", "OBSERVABILITY", "SEARCH")))
                .andExpect(jsonPath("$.checks[?(@.target == 'OBSERVABILITY')].message").value(contains("Not configured")));
        assertThat(acmHubRepository.findByName("hub-new")).isEmpty();

        mockMvc.perform(post("/hubs/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad\",\"apiUrl\":\"ftp://x\",\"credentialsSecretRef\":\"../etc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualCollectionsWaitForTheCollectionLock() throws Exception {
        SimpleLock scheduledCycle = lockProvider.lock(new LockConfiguration(
                Instant.now(), AcmCollectorService.COLLECTION_LOCK, Duration.ofMinutes(5), Duration.ZERO)).orElseThrow();
        try {
            mockMvc.perform(post("/hubs/{id}/collect", hub.getId()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("Another collection is running")));
            mockMvc.perform(post("/clusters/collect"))
                    .andExpect(status().isConflict());
        } finally {
            scheduledCycle.unlock();
        }

        mockMvc.perform(post("/hubs/{id}/collect", hub.getId())).andExpect(status().isOk());
    }

    @Test
    void unknownHubsAreNotFound() throws Exception {
        mockMvc.perform(get("/hubs/{id}/sync-runs", UNKNOWN_ID)).andExpect(status().isNotFound());
        mockMvc.perform(post("/hubs/{id}/test", UNKNOWN_ID)).andExpect(status().isNotFound());
        mockMvc.perform(post("/hubs/{id}/collect", UNKNOWN_ID)).andExpect(status().isNotFound());
    }
}
