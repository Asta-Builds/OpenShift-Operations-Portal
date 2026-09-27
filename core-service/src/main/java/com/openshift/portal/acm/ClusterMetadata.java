package com.openshift.portal.acm;

/**
 * Descriptive facts a hub reports about a managed cluster, used to register clusters the portal has not seen yet.
 * Every field may be null when the hub does not report it.
 *
 * @param environment    value of the cluster's {@code environment} label
 * @param platform       ClusterClaim {@code platform.open-cluster-management.io}, e.g. AWS, VSphere, BareMetal
 * @param openshiftVersion ClusterClaim {@code version.openshift.io}
 * @param region         ClusterClaim {@code region.open-cluster-management.io}
 */
public record ClusterMetadata(String environment, String platform, String openshiftVersion, String region) {
}
