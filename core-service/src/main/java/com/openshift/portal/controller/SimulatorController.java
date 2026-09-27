package com.openshift.portal.controller;

import com.openshift.portal.service.AcmSimulatorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/simulator")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "openshift.portal.simulator", name = "enabled", havingValue = "true")
@Tag(name = "Simulator", description = "Synthetic fleet telemetry generator and chaos injection controls")
public class SimulatorController {

    private final AcmSimulatorService simulatorService;

    @PostMapping("/seed")
    public ResponseEntity<Map<String, String>> seedFleet() {
        boolean seeded = simulatorService.seedInitialFleetIfEmpty();
        return ResponseEntity.ok(Map.of("message", seeded
                ? "Seeded simulated hubs, clusters, namespaces and 30 days of snapshots."
                : "The fleet already contains clusters; nothing was changed."));
    }

    @PostMapping("/fault")
    public ResponseEntity<Map<String, Object>> injectFault(@RequestParam(defaultValue = "true") boolean fail) {
        simulatorService.setSimulateFailure(fail);
        return ResponseEntity.ok(Map.of(
                "simulateFailure", fail,
                "message", fail ? "ACM Hub connection failure will be simulated on next collection." : "Failure injection disabled."
        ));
    }

    @PostMapping("/outage")
    public ResponseEntity<Map<String, Object>> setHubOutage(@RequestParam String hub,
                                                            @RequestParam(defaultValue = "true") boolean down) {
        simulatorService.setHubOutage(hub, down);
        return ResponseEntity.ok(Map.of(
                "hub", hub,
                "down", down,
                "message", down ? "Every call to ACM Hub " + hub + " will fail until the outage is cleared."
                        : "Outage of ACM Hub " + hub + " cleared."
        ));
    }

    @PostMapping("/cluster-failure")
    public ResponseEntity<Map<String, Object>> setClusterFailure(@RequestParam String cluster,
                                                                 @RequestParam(defaultValue = "true") boolean failing) {
        simulatorService.setClusterFailure(cluster, failing);
        return ResponseEntity.ok(Map.of(
                "cluster", cluster,
                "failing", failing,
                "message", failing ? "Cluster " + cluster + " will fail to report until cleared."
                        : "Failure of cluster " + cluster + " cleared."
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSimulatorStatus() {
        return ResponseEntity.ok(Map.of(
                "simulateFailureActive", simulatorService.isSimulateFailure(),
                "hubOutages", simulatorService.getHubOutages(),
                "failingClusters", simulatorService.getFailingClusters()
        ));
    }
}
