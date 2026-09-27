package com.openshift.portal.service;

import com.openshift.portal.dto.AttributionReportDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** The cost attribution report as table rows, shared by the CSV and PDF exports. */
final class AttributionTable {

    static final String[] HEADER = {"Owner Team", "Cost Center", "Namespaces", "Clusters", "CPU Requests (cores)",
            "Memory Requests (GB)", "CPU Usage (cores)", "Memory Usage (GB)", "PVC Requests (GB)", "CPU Share %"};

    private AttributionTable() {
    }

    /** Team rows, then Unattributed, then the total and the cluster-level requests it should match. */
    static List<String[]> rows(AttributionReportDto report) {
        List<String[]> rows = new ArrayList<>();
        report.getTeams().forEach(row -> rows.add(row(row)));
        rows.add(row(report.getUnattributed()));
        rows.add(row(report.getTotal()));
        rows.add(new String[]{"Cluster-level requests", "", "", "", text(report.getClusterCpuRequestCores()),
                text(report.getClusterMemoryRequestGb()), "", "", "",
                report.getCpuCoveragePercent() != null ? "coverage " + report.getCpuCoveragePercent() + "%" : ""});
        return rows;
    }

    static String period(AttributionReportDto report) {
        return "Averages from " + report.getFrom() + " to " + report.getTo()
                + (report.getEnvironment() != null ? ", " + report.getEnvironment() + " clusters" : ", all environments")
                + ", over the " + report.getCollectionsWithNamespaceData() + " of " + report.getCollections()
                + " cluster collections that carried namespace data";
    }

    private static String[] row(AttributionReportDto.Row row) {
        return new String[]{
                row.getTeamName(),
                row.getCostCenter() != null ? row.getCostCenter() : "",
                String.valueOf(row.getNamespaceCount()),
                String.valueOf(row.getClusterCount()),
                text(row.getCpuRequestCores()),
                text(row.getMemoryRequestGb()),
                text(row.getCpuUsageCores()),
                text(row.getMemoryUsageGb()),
                text(row.getPvcRequestGb()),
                text(row.getCpuSharePercent())
        };
    }

    private static String text(BigDecimal value) {
        return value != null ? value.toPlainString() : "n/a";
    }
}
