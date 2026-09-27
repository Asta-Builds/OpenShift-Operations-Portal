package com.openshift.portal.controller;

import com.openshift.portal.acm.HubResilience;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.HubSyncRun;
import com.openshift.portal.dto.AcmHubSummaryDto;
import com.openshift.portal.dto.RegisterHubRequest;
import com.openshift.portal.exception.ResourceNotFoundException;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.HubSyncRunRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hubs")
@RequiredArgsConstructor
public class HubController {

    private final AcmHubRepository acmHubRepository;
    private final HubSyncRunRepository syncRunRepository;
    private final HubResilience hubResilience;

    @GetMapping
    public ResponseEntity<List<AcmHubSummaryDto>> getHubs() {
        return ResponseEntity.ok(acmHubRepository.findAll(Sort.by("name")).stream().map(this::toSummary).toList());
    }

    /** Admin only (any non-GET without its own rule requires ADMIN). The first collection discovers its clusters. */
    @PostMapping
    public ResponseEntity<AcmHubSummaryDto> registerHub(@Valid @RequestBody RegisterHubRequest request) {
        if (acmHubRepository.findByName(request.getName()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An ACM hub named " + request.getName() + " already exists");
        }
        AcmHub hub = acmHubRepository.save(AcmHub.builder()
                .name(request.getName())
                .apiUrl(request.getApiUrl())
                .credentialsSecretRef(request.getCredentialsSecretRef())
                .observabilityUrl(request.getObservabilityUrl())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(hub));
    }

    /** Removes the hub together with its clusters and their snapshots. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHub(@PathVariable UUID id) {
        AcmHub hub = acmHubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ACM hub not found with ID: " + id));
        acmHubRepository.delete(hub);
        return ResponseEntity.noContent().build();
    }

    private AcmHubSummaryDto toSummary(AcmHub hub) {
        return AcmHubSummaryDto.builder()
                .id(hub.getId())
                .name(hub.getName())
                .apiUrl(hub.getApiUrl())
                .credentialsSecretRef(hub.getCredentialsSecretRef())
                .observabilityUrl(hub.getObservabilityUrl())
                .status(hub.getStatus())
                .lastSyncTimestamp(hub.getLastSyncTimestamp())
                .consecutiveFailures(hub.getConsecutiveFailures())
                .circuitBreakerState(hubResilience.circuitState(hub).name())
                .latestSyncRun(syncRunRepository.findTopByHubIdOrderByIdDesc(hub.getId())
                        .map(this::toSyncRunDto)
                        .orElse(null))
                .build();
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
