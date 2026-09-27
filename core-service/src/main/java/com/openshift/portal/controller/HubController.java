package com.openshift.portal.controller;

import com.openshift.portal.acm.HubResilience;
import com.openshift.portal.domain.entity.HubSyncRun;
import com.openshift.portal.dto.AcmHubSummaryDto;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.HubSyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/hubs")
@RequiredArgsConstructor
public class HubController {

    private final AcmHubRepository acmHubRepository;
    private final HubSyncRunRepository syncRunRepository;
    private final HubResilience hubResilience;

    @GetMapping
    public ResponseEntity<List<AcmHubSummaryDto>> getHubs() {
        List<AcmHubSummaryDto> hubs = acmHubRepository.findAll(Sort.by("name")).stream()
                .map(hub -> AcmHubSummaryDto.builder()
                        .id(hub.getId())
                        .name(hub.getName())
                        .apiUrl(hub.getApiUrl())
                        .status(hub.getStatus())
                        .lastSyncTimestamp(hub.getLastSyncTimestamp())
                        .consecutiveFailures(hub.getConsecutiveFailures())
                        .circuitBreakerState(hubResilience.circuitState(hub).name())
                        .latestSyncRun(syncRunRepository.findTopByHubIdOrderByIdDesc(hub.getId())
                                .map(this::toSyncRunDto)
                                .orElse(null))
                        .build())
                .toList();
        return ResponseEntity.ok(hubs);
    }

    private AcmHubSummaryDto.SyncRunDto toSyncRunDto(HubSyncRun run) {
        return AcmHubSummaryDto.SyncRunDto.builder()
                .status(run.getStatus())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .attempts(run.getAttempts())
                .clustersOk(run.getClustersOk())
                .clustersFailed(run.getClustersFailed())
                .errorMessage(run.getErrorMessage())
                .build();
    }
}
