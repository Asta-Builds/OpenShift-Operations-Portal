package com.openshift.portal;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.HubSyncRun;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.SyncStatus;
import com.openshift.portal.dto.SnapshotTriggerResultDto;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.HubSyncRunRepository;
import com.openshift.portal.service.AcmCollectorService;
import com.openshift.portal.service.AcmSimulatorService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hub failures against the real Spring wiring (retry, per-hub circuit breakers, persistence), with short retry
 * waits so the suite stays fast.
 */
@SpringBootTest(properties = "resilience4j.retry.configs.acmHub.wait-duration=10ms")
@ActiveProfiles("dev")
class ResilientCollectionIntegrationTest {

    private static final String HUB_A = "acm-hub-primary-eu";
    private static final String HUB_B = "acm-hub-secondary-us";

    @Autowired
    private AcmCollectorService collectorService;
    @Autowired
    private AcmSimulatorService simulatorService;
    @Autowired
    private AcmHubRepository acmHubRepository;
    @Autowired
    private HubSyncRunRepository syncRunRepository;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void clearFaultsAndBreakers() {
        simulatorService.clearFaults();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void oneShotFaultIsRetriedAndSucceeds() {
        simulatorService.setSimulateFailure(true);

        SnapshotTriggerResultDto result = collectorService.triggerCollection();

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        // Hubs are polled by name, so the fault hit hub A: its first attempt failed and the retry succeeded
        assertThat(latestRuns(HUB_A, 1).get(0).getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(latestRuns(HUB_A, 1).get(0).getAttempts()).isEqualTo(2);
        assertThat(latestRuns(HUB_B, 1).get(0).getAttempts()).isEqualTo(1);
    }

    @Test
    void persistentOutageOpensOnlyThatHubsBreakerWhileOtherHubsKeepCollecting() {
        int failuresBefore = hub(HUB_A).getConsecutiveFailures();
        simulatorService.setHubOutage(HUB_A, true);

        for (int cycle = 0; cycle < 3; cycle++) {
            collectorService.triggerCollection();
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("hub:" + HUB_A).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreakerRegistry.circuitBreaker("hub:" + HUB_B).getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Newest first: the breaker opened during the second cycle, so the third one never called hub A
        List<HubSyncRun> runsA = latestRuns(HUB_A, 3);
        assertThat(runsA).extracting(HubSyncRun::getStatus)
                .containsExactly(SyncStatus.SKIPPED_CIRCUIT_OPEN, SyncStatus.FAILED, SyncStatus.FAILED);
        assertThat(runsA).extracting(HubSyncRun::getAttempts).containsExactly(0, 2, 3);
        assertThat(latestRuns(HUB_B, 3)).extracting(HubSyncRun::getStatus).containsOnly(SyncStatus.SUCCESS);

        assertThat(hub(HUB_A).getStatus()).isEqualTo(HubStatus.UNREACHABLE);
        assertThat(hub(HUB_A).getConsecutiveFailures()).isEqualTo(failuresBefore + 3);
        assertThat(hub(HUB_B).getStatus()).isEqualTo(HubStatus.ACTIVE);
        assertThat(hub(HUB_B).getConsecutiveFailures()).isZero();
    }

    @Test
    void failingClusterMarksTheRunPartialWithoutAbortingItsHub() {
        simulatorService.setClusterFailure("ocp-dev-us-east-sandbox", true);

        collectorService.triggerCollection();

        HubSyncRun run = latestRuns(HUB_B, 1).get(0);
        assertThat(run.getStatus()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(run.getClustersOk()).isEqualTo(1);
        assertThat(run.getClustersFailed()).isEqualTo(1);
        assertThat(hub(HUB_B).getStatus()).isEqualTo(HubStatus.DEGRADED);
    }

    private AcmHub hub(String name) {
        return acmHubRepository.findByName(name).orElseThrow();
    }

    private List<HubSyncRun> latestRuns(String hubName, int count) {
        return syncRunRepository.findByHubIdOrderByIdDesc(hub(hubName).getId(), PageRequest.of(0, count));
    }
}
