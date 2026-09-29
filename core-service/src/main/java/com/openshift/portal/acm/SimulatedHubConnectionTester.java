package com.openshift.portal.acm;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.dto.HubConnectionTestDto;
import com.openshift.portal.dto.HubConnectionTestDto.Check;
import com.openshift.portal.dto.HubConnectionTestDto.Status;
import com.openshift.portal.dto.HubConnectionTestDto.Target;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.service.AcmSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Answers like the simulated hubs: nothing is contacted, and a hub in a simulated outage fails. */
@Component
@ConditionalOnProperty(prefix = "openshift.portal.simulator", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SimulatedHubConnectionTester implements HubConnectionTester {

    private final AcmSimulatorService simulatorService;
    private final ClusterRepository clusterRepository;

    @Override
    public HubConnectionTestDto test(AcmHub hub) {
        Check api;
        if (hub.getName() != null && simulatorService.getHubOutages().contains(hub.getName())) {
            api = new Check(Target.API, Status.FAILED, "Simulated outage of ACM Hub: " + hub.getApiUrl(), 0);
        } else {
            long clusters = hub.getId() != null ? clusterRepository.countByAcmHubId(hub.getId()) : 0;
            api = new Check(Target.API, Status.OK, clusters + " simulated clusters", 0);
        }
        return HubConnectionTestDto.of(List.of(
                new Check(Target.CREDENTIALS, Status.SKIPPED, "Simulator: no Secret is read", 0),
                api,
                optional(Target.OBSERVABILITY, hub.getObservabilityUrl()),
                optional(Target.SEARCH, hub.getSearchUrl())));
    }

    private static Check optional(Target target, String url) {
        return url == null || url.isBlank()
                ? new Check(target, Status.SKIPPED, "Not configured", 0)
                : new Check(target, Status.SKIPPED, "Simulator: not contacted", 0);
    }
}
