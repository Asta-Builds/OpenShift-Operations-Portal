package com.openshift.portal.controller;

import com.openshift.portal.acm.HubConnectionTester;
import com.openshift.portal.acm.HubCredentialsResolver;
import com.openshift.portal.acm.HubResilience;
import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.HubSyncRun;
import com.openshift.portal.dto.AcmHubSummaryDto;
import com.openshift.portal.dto.HubConnectionTestDto;
import com.openshift.portal.dto.RegisterHubRequest;
import com.openshift.portal.dto.UpdateHubRequest;
import com.openshift.portal.exception.ResourceNotFoundException;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.HubSyncRunRepository;
import com.openshift.portal.service.ManualCollectionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
@Tag(name = "ACM Hubs", description = "Advanced Cluster Management hubs registration, synchronization, and telemetry status")
public class HubController {

    private static final int MAX_SYNC_RUNS = 100;

    private final AcmHubRepository acmHubRepository;
    private final HubSyncRunRepository syncRunRepository;
    private final ClusterRepository clusterRepository;
    private final HubResilience hubResilience;
    private final HubCredentialsResolver credentialsResolver;
    private final HubConnectionTester connectionTester;
    private final ManualCollectionService manualCollection;
    private final AcmProperties properties;

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
                .searchUrl(request.getSearchUrl())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(hub));
    }

    /** Admin only. Changes the hub's endpoints or credentials Secret while keeping its clusters and history. */
    @PatchMapping("/{id}")
    public ResponseEntity<AcmHubSummaryDto> updateHub(@PathVariable UUID id, @Valid @RequestBody UpdateHubRequest request) {
        AcmHub hub = findHub(id);
        if (request.getApiUrl() != null) {
            hub.setApiUrl(request.getApiUrl());
        }
        if (request.getCredentialsSecretRef() != null) {
            hub.setCredentialsSecretRef(request.getCredentialsSecretRef());
        }
        if (request.getObservabilityUrl() != null) {
            hub.setObservabilityUrl(request.getObservabilityUrl().isEmpty() ? null : request.getObservabilityUrl());
        }
        if (request.getSearchUrl() != null) {
            hub.setSearchUrl(request.getSearchUrl().isEmpty() ? null : request.getSearchUrl());
        }
        return ResponseEntity.ok(toSummary(acmHubRepository.save(hub)));
    }

    /** The hub's most recent collections, newest first. */
    @GetMapping("/{id}/sync-runs")
    public ResponseEntity<List<AcmHubSummaryDto.SyncRunDto>> getSyncRuns(@PathVariable UUID id,
                                                                         @RequestParam(defaultValue = "20") int limit) {
        AcmHub hub = findHub(id);
        return ResponseEntity.ok(syncRunRepository
                .findByHubIdOrderByIdDesc(hub.getId(), PageRequest.of(0, Math.max(1, Math.min(limit, MAX_SYNC_RUNS))))
                .stream().map(this::toSyncRunDto).toList());
    }

    /**
     * Admin only: tests settings before they are registered. Like registering, it can send a mounted hub token to
     * any URL, which is why only admins may.
     */
    @PostMapping("/test")
    public ResponseEntity<HubConnectionTestDto> testNewHub(@Valid @RequestBody RegisterHubRequest request) {
        return ResponseEntity.ok(connectionTester.test(AcmHub.builder()
                .name(request.getName())
                .apiUrl(request.getApiUrl())
                .credentialsSecretRef(request.getCredentialsSecretRef())
                .observabilityUrl(request.getObservabilityUrl())
                .searchUrl(request.getSearchUrl())
                .build()));
    }

    /** Operators too: it only calls the hub's registered endpoints. */
    @PostMapping("/{id}/test")
    public ResponseEntity<HubConnectionTestDto> testHub(@PathVariable UUID id) {
        return ResponseEntity.ok(connectionTester.test(findHub(id)));
    }

    /** Operators: collects this hub now; 409 while another collection holds the lock. */
    @PostMapping("/{id}/collect")
    public ResponseEntity<AcmHubSummaryDto.SyncRunDto> collectHub(@PathVariable UUID id) {
        return ResponseEntity.ok(toSyncRunDto(manualCollection.collectHub(findHub(id))));
    }

    /** Removes the hub together with its clusters and their snapshots. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHub(@PathVariable UUID id) {
        AcmHub hub = findHub(id);
        acmHubRepository.delete(hub);
        return ResponseEntity.noContent().build();
    }

    private AcmHub findHub(UUID id) {
        return acmHubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ACM hub not found with ID: " + id));
    }

    private AcmHubSummaryDto toSummary(AcmHub hub) {
        return AcmHubSummaryDto.builder()
                .id(hub.getId())
                .name(hub.getName())
                .apiUrl(hub.getApiUrl())
                .credentialsSecretRef(hub.getCredentialsSecretRef())
                .observabilityUrl(hub.getObservabilityUrl())
                .searchUrl(hub.getSearchUrl())
                .status(hub.getStatus())
                .lastSyncTimestamp(hub.getLastSyncTimestamp())
                .consecutiveFailures(hub.getConsecutiveFailures())
                .circuitBreakerState(hubResilience.circuitState(hub).name())
                .clusterCount(clusterRepository.countByAcmHubId(hub.getId()))
                // Simulated hubs read no Secret
                .credentialsMounted(properties.getSimulator().isEnabled() ? null : credentialsResolver.hasToken(hub))
                .latestSyncRun(syncRunRepository.findTopByHubIdOrderByIdDesc(hub.getId())
                        .map(this::toSyncRunDto)
                        .orElse(null))
                .build();
    }

    private AcmHubSummaryDto.SyncRunDto toSyncRunDto(HubSyncRun run) {
        return AcmHubSummaryDto.SyncRunDto.builder()
                .id(run.getId())
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
