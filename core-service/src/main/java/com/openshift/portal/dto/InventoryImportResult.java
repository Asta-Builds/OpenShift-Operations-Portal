package com.openshift.portal.dto;

/**
 * Outcome of an inventory import.
 *
 * @param matchedNodes nodes of the clusters' latest snapshots now matched to an inventory row, from any source
 */
public record InventoryImportResult(String source, int rows, int inserted, int updated, int removed, int matchedNodes) {
}
