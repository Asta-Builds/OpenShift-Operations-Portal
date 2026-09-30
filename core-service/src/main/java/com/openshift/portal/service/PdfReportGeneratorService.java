package com.openshift.portal.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.enums.ReportType;
import com.openshift.portal.dto.AttributionReportDto;
import com.openshift.portal.dto.FinAiChartDto;
import com.openshift.portal.dto.FinAiChartDataPointDto;
import com.openshift.portal.dto.FinAiCliSnippetDto;
import com.openshift.portal.dto.FinAiMetricItemDto;
import com.openshift.portal.dto.FinAiResponseDto;
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
                    table.addCell(createCell(snap.hasNodeData() ? String.valueOf(snap.getWorkerNodes()) : "-", true));
                    table.addCell(createCell(licenseCores(snap), true));
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
                    table.addCell(createCell(snap.getAllocatedCpuCores().stripTrailingZeros().toPlainString() + " / " + snap.getTotalCpuCores(), true));
                    table.addCell(createCell(snap.getAllocatedMemoryGb() + " / " + snap.getTotalMemoryGb() + " GB", true));
                    table.addCell(createCell(snap.getAllocatedStorageGb() + " / " + snap.getTotalStorageGb() + " GB", true));
                    table.addCell(createCell(snap.hasNodeData() ? String.valueOf(snap.getTotalNodes()) : "-", true));
                    table.addCell(createCell(licenseCores(snap), true));
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
            long withoutNodeData = latestSnapshots.stream().filter(snap -> !snap.hasNodeData()).count();
            if (type != ReportType.COST_ATTRIBUTION && withoutNodeData > 0) {
                document.add(new Paragraph("* " + withoutNodeData + " cluster(s) have no node data (no fresh node agent "
                        + "report), so their license cores are unknown and not included in any total.", footerFont));
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Error rendering PDF report: " + e.getMessage(), e);
        }
    }

    public byte[] generateFinAiPdf(FinAiResponseDto response) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Header Banner
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(204, 0, 0));
            Paragraph title = new Paragraph("OpenShift Operations Portal — FinAI Copilot Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4);
            document.add(title);

            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Paragraph subtitle = new Paragraph("Généré le: " + timestamp + " | Moteur: FinAI Heuristic Engine v1.3 | Classification: INTERNE", subFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(14);
            document.add(subtitle);

            // Headline
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(30, 41, 59));
            Paragraph head = new Paragraph("Synthèse Exécutive : " + (response.getHeadline() != null ? response.getHeadline() : "Diagnostic FinOps"), headFont);
            head.setSpacingAfter(8);
            document.add(head);

            // Metrics Table
            if (response.getMetrics() != null && !response.getMetrics().isEmpty()) {
                PdfPTable table = new PdfPTable(2);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{3.0f, 2.0f});
                addHeaderCells(table, new String[]{"Indicateur Clé", "Valeur / Statut"});
                for (FinAiMetricItemDto m : response.getMetrics()) {
                    table.addCell(createCell(m.getLabel(), false));
                    table.addCell(createCell(m.getValue(), true));
                }
                table.setSpacingAfter(12);
                document.add(table);
            }

            // Analysis
            if (response.getAnalysisMarkdown() != null) {
                Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
                String clean = response.getAnalysisMarkdown()
                        .replaceAll("### ", "")
                        .replaceAll("#### ", "")
                        .replaceAll("\\*\\*", "")
                        .replaceAll("> \\[!(WARNING|NOTE)\\]", "NOTE:");
                Paragraph body = new Paragraph(clean, bodyFont);
                body.setSpacingAfter(14);
                document.add(body);
            }

            // Interactive Charts Visual Data Breakdown
            if (response.getCharts() != null && !response.getCharts().isEmpty()) {
                Font chartHeadFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(30, 41, 59));
                for (FinAiChartDto chart : response.getCharts()) {
                    Paragraph cp = new Paragraph("📊 " + chart.getTitle() + " [" + chart.getTotalValue() + "]", chartHeadFont);
                    cp.setSpacingBefore(6);
                    cp.setSpacingAfter(4);
                    document.add(cp);

                    PdfPTable chartTable = new PdfPTable(2);
                    chartTable.setWidthPercentage(100);
                    chartTable.setWidths(new float[]{3.5f, 1.5f});
                    addHeaderCells(chartTable, new String[]{"Indicateur / Segment", "Valeur"});
                    for (FinAiChartDataPointDto pt : chart.getPoints()) {
                        chartTable.addCell(createCell(pt.getLabel(), false));
                        chartTable.addCell(createCell(pt.getFormattedValue() != null ? pt.getFormattedValue() : String.valueOf(pt.getValue()), true));
                    }
                    chartTable.setSpacingAfter(8);
                    document.add(chartTable);
                }
            }

            // CLI Commands Table
            if (response.getCliCommands() != null && !response.getCliCommands().isEmpty()) {
                Font cliHeadFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(30, 41, 59));
                document.add(new Paragraph("Commandes OpenShift CLI Correctives :", cliHeadFont));
                PdfPTable cliTable = new PdfPTable(3);
                cliTable.setWidthPercentage(100);
                cliTable.setWidths(new float[]{2.0f, 1.2f, 3.8f});
                addHeaderCells(cliTable, new String[]{"Action", "Namespace", "Commande oc"});
                Font codeFont = FontFactory.getFont(FontFactory.COURIER, 8, new Color(0, 100, 0));
                for (FinAiCliSnippetDto c : response.getCliCommands()) {
                    cliTable.addCell(createCell(c.getTitle(), false));
                    cliTable.addCell(createCell(c.getTargetNamespace() != null ? c.getTargetNamespace() : "all", false));
                    PdfPCell codeCell = new PdfPCell(new Phrase(c.getCommand(), codeFont));
                    codeCell.setPadding(4);
                    cliTable.addCell(codeCell);
                }
                cliTable.setSpacingAfter(12);
                document.add(cliTable);
            }

            // Execution Plan
            if (response.getExecutionPlan() != null && !response.getExecutionPlan().isEmpty()) {
                Font planHeadFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(30, 41, 59));
                document.add(new Paragraph("Plan d'Exécution Recommandé :", planHeadFont));
                com.lowagie.text.List list = new com.lowagie.text.List(com.lowagie.text.List.ORDERED, 15);
                Font listFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
                for (String step : response.getExecutionPlan()) {
                    list.add(new ListItem(step, listFont));
                }
                document.add(list);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate FinAI PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Error rendering FinAI PDF report: " + e.getMessage(), e);
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

    /** Unknown without node data, which is not the same as 0. */
    private static String licenseCores(ClusterSnapshot snap) {
        return snap.hasNodeData() ? String.valueOf(snap.getLicenseCoresCount()) : "no node data";
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
