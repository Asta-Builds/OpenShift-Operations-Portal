package com.openshift.portal.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.openshift.portal.domain.entity.InfrastructureInventory;
import com.openshift.portal.domain.enums.ProviderType;
import com.openshift.portal.dto.InventoryImportResult;
import com.openshift.portal.exception.InventoryImportException;
import com.openshift.portal.repository.InfrastructureInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads infrastructure inventory from CSV, the form a CMDB export or an air-gapped asset database can produce.
 *
 * <p>Columns (header row, any order, case-insensitive): {@code provider_id} (a node providerID, parsed like the
 * node's) and/or {@code provider_type} with {@code instance_key}, used on rows whose provider_id is empty; optionally {@code hypervisor_host},
 * {@code hypervisor_cluster}, {@code datacenter}, {@code physical_sockets}, {@code physical_cores} and
 * {@code threads_per_core}. The whole file is validated first and applied in one transaction, so a file with errors
 * changes nothing.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryImportService {

    public static final List<String> COLUMNS = List.of("provider_id", "provider_type", "instance_key", "hypervisor_host",
            "hypervisor_cluster", "datacenter", "physical_sockets", "physical_cores", "threads_per_core");

    private static final int MAX_ERRORS = 50;

    private final InfrastructureInventoryRepository inventoryRepository;
    private final ProviderIdParserService parser;
    private final NodeCorrelationService nodeCorrelationService;

    /**
     * @param source  where the rows come from, e.g. CMDB; rows are keyed per source
     * @param replace when true, rows of this source missing from the file are removed (a full export);
     *                otherwise the file only adds and updates rows
     * @throws InventoryImportException when any row is invalid; nothing is changed then
     */
    @Transactional
    public InventoryImportResult importCsv(Reader csv, String source, boolean replace) {
        List<InfrastructureInventory> parsed = parse(csv, source);

        Map<String, InfrastructureInventory> existing = inventoryRepository.findBySource(source).stream()
                .collect(Collectors.toMap(InventoryImportService::key, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        int inserted = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        for (InfrastructureInventory row : parsed) {
            seen.add(key(row));
            InfrastructureInventory current = existing.get(key(row));
            if (current == null) {
                row.setSyncedAt(now);
                inventoryRepository.save(row);
                inserted++;
            } else {
                current.setHypervisorHost(row.getHypervisorHost());
                current.setHypervisorCluster(row.getHypervisorCluster());
                current.setDatacenter(row.getDatacenter());
                current.setPhysicalSockets(row.getPhysicalSockets());
                current.setPhysicalCores(row.getPhysicalCores());
                current.setThreadsPerCore(row.getThreadsPerCore());
                current.setSyncedAt(now);
                updated++;
            }
        }
        int removed = 0;
        if (replace) {
            List<InfrastructureInventory> gone = existing.values().stream().filter(row -> !seen.contains(key(row))).toList();
            inventoryRepository.deleteAll(gone);
            removed = gone.size();
        }
        inventoryRepository.flush();

        int matched = nodeCorrelationService.recorrelateLatest();
        log.info("Imported {} inventory: {} added, {} updated, {} removed; {} current nodes matched", source, inserted,
                updated, removed, matched);
        return new InventoryImportResult(source, parsed.size(), inserted, updated, removed, matched);
    }

    /** Removes every row of one source; its nodes lose their correlation right away. */
    @Transactional
    public void deleteSource(String source) {
        inventoryRepository.deleteAll(inventoryRepository.findBySource(source));
        inventoryRepository.flush();
        nodeCorrelationService.recorrelateLatest();
    }

    private List<InfrastructureInventory> parse(Reader csv, String source) {
        List<String[]> lines;
        try (CSVReader reader = new CSVReader(csv)) {
            lines = reader.readAll();
        } catch (IOException | CsvException e) {
            throw new InventoryImportException(List.of("The file is not readable CSV: " + e.getMessage()));
        }
        if (lines.isEmpty()) {
            throw new InventoryImportException(List.of("The file is empty; it needs a header row"));
        }

        Map<String, Integer> columns = new HashMap<>();
        String[] header = lines.get(0);
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < header.length; i++) {
            String name = header[i].replace("﻿", "").trim().toLowerCase(Locale.ROOT);
            if (!COLUMNS.contains(name)) {
                errors.add("Unknown column \"" + header[i].trim() + "\"; expected " + COLUMNS);
            }
            columns.put(name, i);
        }
        if (!columns.containsKey("provider_id") && !(columns.containsKey("provider_type") && columns.containsKey("instance_key"))) {
            errors.add("Identify machines with a provider_id column, or provider_type and instance_key columns");
        }
        if (!errors.isEmpty()) {
            throw new InventoryImportException(errors);
        }

        Map<String, InfrastructureInventory> rows = new LinkedHashMap<>();
        for (int n = 1; n < lines.size() && errors.size() < MAX_ERRORS; n++) {
            String[] line = lines.get(n);
            if (line.length == 1 && line[0].isBlank()) {
                continue;
            }
            int lineNumber = n + 1;
            ProviderType type;
            String instanceKey;
            String providerId = value(line, columns, "provider_id");
            if (providerId != null) {
                ProviderIdParserService.ProviderReference ref = parser.parse(providerId);
                type = ref.type();
                instanceKey = ref.instanceKey();
            } else {
                String typeName = value(line, columns, "provider_type");
                try {
                    type = ProviderType.valueOf(typeName == null ? "" : typeName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    errors.add("Line " + lineNumber + (typeName == null
                            ? ": needs a provider_id, or a provider_type and instance_key"
                            : ": unknown provider_type \"" + typeName + "\""));
                    continue;
                }
                instanceKey = ProviderIdParserService.normalizeKey(type, value(line, columns, "instance_key"));
                if (type == ProviderType.UNKNOWN || instanceKey == null || instanceKey.isEmpty()) {
                    errors.add("Line " + lineNumber + ": provider_type and instance_key are required");
                    continue;
                }
            }

            InfrastructureInventory row = InfrastructureInventory.builder()
                    .source(source)
                    .providerType(type)
                    .instanceKey(instanceKey)
                    .hypervisorHost(value(line, columns, "hypervisor_host"))
                    .hypervisorCluster(value(line, columns, "hypervisor_cluster"))
                    .datacenter(value(line, columns, "datacenter"))
                    .physicalSockets(count(line, columns, "physical_sockets", lineNumber, errors))
                    .physicalCores(count(line, columns, "physical_cores", lineNumber, errors))
                    .threadsPerCore(count(line, columns, "threads_per_core", lineNumber, errors))
                    .build();
            if (rows.putIfAbsent(key(row), row) != null) {
                errors.add("Line " + lineNumber + ": " + type + " " + instanceKey + " appears more than once");
            }
        }
        if (!errors.isEmpty()) {
            throw new InventoryImportException(errors);
        }
        return new ArrayList<>(rows.values());
    }

    private static String value(String[] line, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= line.length) {
            return null;
        }
        String value = line[index].trim();
        return value.isEmpty() ? null : value;
    }

    /** A positive whole number, or null when the cell is empty (unknown). */
    private static Integer count(String[] line, Map<String, Integer> columns, String column, int lineNumber,
                                 List<String> errors) {
        String value = value(line, columns, column);
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0 && parsed <= 4096) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        errors.add("Line " + lineNumber + ": " + column + " must be a whole number from 1 to 4096, not \"" + value + "\"");
        return null;
    }

    private static String key(InfrastructureInventory row) {
        return row.getProviderType() + "|" + row.getInstanceKey();
    }
}
