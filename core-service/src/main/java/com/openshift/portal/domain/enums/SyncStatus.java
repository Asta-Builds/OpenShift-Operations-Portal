package com.openshift.portal.domain.enums;

public enum SyncStatus {
    /** Every cluster the hub reported was collected. */
    SUCCESS,
    /** The hub answered, but some clusters could not be collected. */
    PARTIAL,
    /** No cluster data was collected: the hub was unreachable or every cluster failed. */
    FAILED,
    /** The hub's circuit breaker was open, so it was not called at all. */
    SKIPPED_CIRCUIT_OPEN
}
