package com.openshift.portal.controller;

import com.openshift.portal.domain.entity.InfrastructureInventory;
import com.openshift.portal.dto.InfrastructureTopologyDto;
import com.openshift.portal.dto.InventoryImportResult;
import com.openshift.portal.dto.TopologyGraphDto;
import com.openshift.portal.repository.InfrastructureInventoryRepository;
import com.openshift.portal.service.InfrastructureTopologyService;
import com.openshift.portal.service.InventoryImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Infrastructure topology and the inventory it is built from. Reading is open to viewers; changes are admin only. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Infrastructure", description = "Hypervisor and hardware correlation of cluster nodes through the infrastructure inventory")
public class InfrastructureController {

    private static final Pattern SOURCE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{1,29}");

    private final InfrastructureTopologyService topologyService;
    private final InventoryImportService importService;
    private final InfrastructureInventoryRepository inventoryRepository;

    @GetMapping("/infrastructure/topology")
    public ResponseEntity<InfrastructureTopologyDto> topology() {
        return ResponseEntity.ok(topologyService.topology());
    }

    @GetMapping("/infrastructure/topology/graph")
    @Operation(summary = "Get complete interactive D3 topology graph (Hubs, Clusters, Nodes, Namespaces)")
    public ResponseEntity<TopologyGraphDto> topologyGraph() {
        return ResponseEntity.ok(topologyService.buildTopologyGraph());
    }

    @GetMapping("/inventory")
    public ResponseEntity<List<InfrastructureInventory>> inventory(@RequestParam(required = false) String source) {
        return ResponseEntity.ok(source == null
                ? inventoryRepository.findAll(Sort.by("source", "providerType", "instanceKey"))
                : inventoryRepository.findBySource(source.toUpperCase(Locale.ROOT)));
    }

    /**
     * Imports a CSV body (see {@link InventoryImportService} for the columns). With {@code replace=true} the file is a
     * full export of the source and rows missing from it are removed.
     */
    @PostMapping(value = "/inventory/import", consumes = {"text/csv", "text/plain"})
    public ResponseEntity<InventoryImportResult> importCsv(@RequestBody String csv,
                                                           @RequestParam(defaultValue = "CMDB") String source,
                                                           @RequestParam(defaultValue = "false") boolean replace) {
        return ResponseEntity.ok(importService.importCsv(new StringReader(csv), checkedSource(source), replace));
    }

    /** Removes every row of one source. */
    @DeleteMapping("/inventory")
    public ResponseEntity<Void> deleteSource(@RequestParam String source) {
        importService.deleteSource(checkedSource(source));
        return ResponseEntity.noContent().build();
    }

    private static String checkedSource(String source) {
        if (!SOURCE.matcher(source).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source must be a letter followed by 1-29 letters, digits or _");
        }
        String normalized = source.toUpperCase(Locale.ROOT);
        if (InfrastructureInventory.SOURCE_SIMULATOR.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SIMULATOR is reserved for the simulated inventory");
        }
        return normalized;
    }
}
