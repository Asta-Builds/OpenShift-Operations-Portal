package com.openshift.portal.controller;

import com.openshift.portal.dto.NodeAgentStatusDto;
import com.openshift.portal.dto.NodeReportReceiptDto;
import com.openshift.portal.dto.NodeReportRequest;
import com.openshift.portal.service.NodeAgentReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Node reports from the agent in each managed cluster: agents send them, viewers see who reports and when. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Node Agents", description = "Node inventory reported by the node agent running in each managed cluster")
public class NodeAgentController {

    /**
     * Optional token claim naming the one cluster an agent may report. Keycloak adds it with a hardcoded-claim mapper
     * on a per-cluster client; tokens without it may report any cluster.
     */
    static final String CLUSTER_CLAIM = "portal_cluster";

    private final NodeAgentReportService reportService;

    @PostMapping("/node-reports")
    public ResponseEntity<NodeReportReceiptDto> report(@Valid @RequestBody NodeReportRequest request,
                                                       @AuthenticationPrincipal Jwt token) {
        if (token != null && token.hasClaim(CLUSTER_CLAIM)
                && !request.getClusterName().equals(token.getClaimAsString(CLUSTER_CLAIM))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This agent may only report cluster " + token.getClaimAsString(CLUSTER_CLAIM));
        }
        return ResponseEntity.ok(reportService.record(request));
    }

    @GetMapping("/node-reports")
    public ResponseEntity<List<NodeAgentStatusDto>> statuses() {
        return ResponseEntity.ok(reportService.statuses());
    }
}
