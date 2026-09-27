package com.openshift.portal.service;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.exception.InventoryImportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Imports inventory files dropped into a mounted folder, the air-gap friendly way to feed a CMDB export in: each
 * {@code <source>.csv} replaces the rows of that source. One replica imports at a time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryFolderImporter {

    private static final Pattern SOURCE_FILE = Pattern.compile("([A-Za-z][A-Za-z0-9_-]{1,29})\\.csv");

    private final InventoryImportService importService;
    private final AcmProperties properties;

    @Scheduled(cron = "${openshift.portal.inventory.import-cron:0 0 * * * *}")
    @SchedulerLock(name = "inventory-import", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void importFolder() {
        String dir = properties.getInventory().getImportDir();
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path folder = Path.of(dir);
        if (!Files.isDirectory(folder)) {
            log.warn("Inventory import folder {} does not exist", folder);
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.csv")) {
            for (Path file : files) {
                var name = SOURCE_FILE.matcher(file.getFileName().toString());
                if (!name.matches()) {
                    log.warn("Skipping inventory file {}: name it <source>.csv, for example cmdb.csv", file.getFileName());
                    continue;
                }
                String source = name.group(1).toUpperCase(Locale.ROOT).replace('-', '_');
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    importService.importCsv(reader, source, true);
                } catch (InventoryImportException e) {
                    log.error("Inventory file {} was rejected, nothing changed: {}", file.getFileName(), e.getErrors());
                }
            }
        } catch (IOException e) {
            log.error("Cannot read inventory import folder {}", folder, e);
        }
    }
}
