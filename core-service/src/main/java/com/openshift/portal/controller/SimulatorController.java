package com.openshift.portal.controller;

import com.openshift.portal.service.AcmSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/simulator")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "openshift.portal.simulator", name = "enabled", havingValue = "true")
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

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSimulatorStatus() {
        return ResponseEntity.ok(Map.of(
                "simulateFailureActive", simulatorService.isSimulateFailure()
        ));
    }
}
