package com.openshift.portal.acm;

import java.util.List;

/**
 * The namespaces read for one cluster.
 *
 * @param complete true when {@code namespaces} lists every namespace on the cluster, so a namespace missing from it
 *                 has been deleted. False when the list only comes from metrics, which omit namespaces without pods.
 */
public record NamespaceInventory(List<NamespaceObservation> namespaces, boolean complete) {
}
