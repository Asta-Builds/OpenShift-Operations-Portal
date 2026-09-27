package com.openshift.portal.nodeagent.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openshift.portal.nodeagent.node.ReportedNode;

import java.time.Instant;
import java.util.List;

/** Body of {@code POST /node-reports}: every node of one cluster at one moment. */
public record NodeReport(
        String clusterName,
        String agentVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant collectedAt,
        List<ReportedNode> nodes) {
}
