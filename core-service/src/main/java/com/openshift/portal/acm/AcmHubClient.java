package com.openshift.portal.acm;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.exception.AcmConnectionException;

import java.util.List;

/**
 * Reads the state of an ACM hub's managed clusters. Implementations only do remote I/O; the collector adds
 * retries, circuit breaking and persistence.
 */
public interface AcmHubClient {

    /**
     * Returns one observation per managed cluster; a cluster whose data could not be read is reported through
     * {@link ClusterObservation#failed} instead of failing the whole hub.
     *
     * @throws AcmConnectionException when the hub cannot be reached or answers with a 5xx; these calls are retried.
     *         Any other exception, such as an authentication or authorization error, is not retried.
     */
    List<ClusterObservation> fetchClusters(AcmHub hub);
}
