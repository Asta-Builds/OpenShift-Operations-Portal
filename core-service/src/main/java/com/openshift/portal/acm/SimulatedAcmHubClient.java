package com.openshift.portal.acm;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.service.AcmSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Answers hub reads from the simulator's growth model, including any faults injected through the simulator.
 */
@Component
@ConditionalOnProperty(prefix = "openshift.portal.simulator", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SimulatedAcmHubClient implements AcmHubClient {

    private final AcmSimulatorService simulatorService;

    @Override
    public List<ClusterObservation> fetchClusters(AcmHub hub) {
        return simulatorService.observeHub(hub, LocalDateTime.now());
    }
}
