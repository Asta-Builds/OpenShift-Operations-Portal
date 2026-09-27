package com.openshift.portal.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.dto.AttributionReportDto;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportGeneratorService {

    private final ClusterSnapshotRepository snapshotRepository;
    private final AttributionService attributionService;

    @Transactional(readOnly = true)
    public byte[] generatePdfReport(ReportType type) {
        List<ClusterSnapshot> latestSnapshots = snapshotRepository.findLatestSnapshotsForAllClusters();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 40, 40);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Document Header
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(238, 0, 0));
            Paragraph title = new Paragraph("OpenShift Operations Portal — Fleet & Compliance Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6);
            document.add(title);

            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Paragraph subtitle = new Paragraph("Report Type: " + type.name() + " | Generated At: " + timestamp + " | Classification: CONFIDENTIAL", subFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(18);
            document.add(subtitle);

            // Table Header setup
            PdfPTable table;
            if (type == ReportType.COST_ATTRIBUTION) {
                AttributionReportDto report = attributionService.attribute(LocalDate.now().minusDays(29), LocalDate.now(), null);
                Paragraph period = new Paragraph(AttributionTable.period(report), subFont);
                period.setSpacingAfter(8);
                document.add(period);
                table = new PdfPTable(AttributionTable.HEADER.length);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{2.4f, 1.4f, 1.1f, 1.0f, 1.3f, 1.3f, 1.2f, 1.2f, 1.2f, 1.0f});
                addHeaderCells(table, AttributionTable.HEADER);
                for (String[] row : AttributionTable.rows(report)) {
                    for (int i = 0; i < row.length; i++) {
                        table.addCell(createCell(row[i], i >= 2));
                    }
                }
            } else if (type == ReportType.LICENSE_AUDIT) {
                table = new PdfPTable(7);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{2.5f, 1.5f, 2.0f, 1.5f, 1.2f, 1.5f, 2.0f});
                addHeaderCells(table, new String[]{"Cluster Name", "Environment", "Owner Team", "Infrastructure", "Workers", "License Cores", "Collected At"});

                for (ClusterSnapshot snap : latestSnapshots) {
                    Cluster c = snap.getCluster();
                    table.addCell(createCell(c.getClusterName(), false));
                    table.addCell(createCell(c.getEnvironment() != null ? c.getEnvironment().name() : "N/A", false));
                    table.addCell(createCell(c.getOwnerTeam() != null ? c.getOwnerTeam().getName() : "Unassigned", false));
                    table.addCell(createCell(c.getInfrastructureType() != null ? c.getInfrastructureType().name() : "N/A", false));
                    table.addCell(createCell(String.valueOf(snap.getWorkerNodes()), true));
                    table.addCell(createCell(String.valueOf(snap.getLicenseCoresCount()), true));
                    table.addCell(createCell(snap.getSnapshotTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), false));
                }
            } else {
                table = new PdfPTable(8);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{2.2f, 1.4f, 1.5f, 1.5f, 1.5f, 1.5f, 1.0f, 1.4f});
                addHeaderCells(table, new String[]{"Cluster Name", "Environment", "Infrastructure", "CPU (Alloc/Tot)", "Mem (Alloc/Tot)", "Storage (Alloc/Tot)", "Nodes", "License Cores"});

                for (ClusterSnapshot snap : latestSnapshots) {
                    Cluster c = snap.getCluster();
                    table.addCell(createCell(c.getClusterName(), false));
                    table.addCell(createCell(c.getEnvironment() != null ? c.getEnvironment().name() : "N/A", false));
                    table.addCell(createCell(c.getInfrastructureType() != null ? c.getInfrastructureType().name() : "N/A", false));
                    table.addCell(createCell(snap.getAllocatedCpuCores() + " / " + snap.getTotalCpuCores(), true));
                    table.addCell(createCell(snap.getAllocatedMemoryGb() + " / " + snap.getTotalMemoryGb() + " GB", true));
                    table.addCell(createCell(snap.getAllocatedStorageGb() + " / " + snap.getTotalStorageGb() + " GB", true));
                    table.addCell(createCell(String.valueOf(snap.getTotalNodes()), true));
                    table.addCell(createCell(String.valueOf(snap.getLicenseCoresCount()), true));
                }
            }

            document.add(table);

            // How license cores, or attribution, are counted
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);
            Paragraph note = type == ReportType.COST_ATTRIBUTION
                    ? new Paragraph("\n* Namespaces are attributed from their owner label; namespaces without a recognised owner are "
                    + "Unattributed and never assigned to the cluster's owner. Values are averages over the period's collections.", footerFont)
                    : new Paragraph("\n* License cores count the CPU cores of worker nodes only; control-plane and infrastructure "
                    + "nodes are excluded. Hyperthreading and socket-pair subscription rules are not applied yet, so verify "
                    + "these figures against your Red Hat subscription terms before using them for compliance.", footerFont);
            document.add(note);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Error rendering PDF report: " + e.getMessage(), e);
        }
    }

    private void addHeaderCells(PdfPTable table, String[] headers) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Color redHatDark = new Color(31, 41, 55);

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(redHatDark);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private PdfPCell createCell(String text, boolean center) {
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
        PdfPCell cell = new PdfPCell(new Phrase(text, cellFont));
        cell.setPadding(5);
        if (center) {
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        }
        return cell;
    }
}
