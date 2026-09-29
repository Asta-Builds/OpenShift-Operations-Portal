package com.openshift.portal.acm;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.dto.HubConnectionTestDto;

/**
 * Checks, one by one, what a collection needs from a hub, and says which part fails and why. Tests call the hub
 * directly, without the hub's retry or circuit breaker, so they never affect collections.
 */
public interface HubConnectionTester {

    /** {@code hub} may be unsaved, to test settings before registering them. */
    HubConnectionTestDto test(AcmHub hub);
}
