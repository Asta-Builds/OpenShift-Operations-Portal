package com.openshift.portal.dto;

/**
 * Answer to a node report.
 *
 * @param registered false while no ACM hub has reported a cluster of this name, usually a misconfigured cluster name
 */
public record NodeReportReceiptDto(String clusterName, int nodes, boolean registered) {
}
