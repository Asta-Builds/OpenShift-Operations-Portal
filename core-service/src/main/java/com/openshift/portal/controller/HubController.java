package com.openshift.portal.controller;

import com.openshift.portal.dto.AcmHubSummaryDto;
import com.openshift.portal.repository.AcmHubRepository;
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

    @GetMapping
    public ResponseEntity<List<AcmHubSummaryDto>> getHubs() {
        List<AcmHubSummaryDto> hubs = acmHubRepository.findAll(Sort.by("name")).stream()
                .map(hub -> AcmHubSummaryDto.builder()
                        .id(hub.getId())
                        .name(hub.getName())
                        .apiUrl(hub.getApiUrl())
                        .status(hub.getStatus())
                        .lastSyncTimestamp(hub.getLastSyncTimestamp())
                        .build())
                .toList();
        return ResponseEntity.ok(hubs);
    }
}
