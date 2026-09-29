package com.openshift.portal.dto;

import java.util.List;

/**
 * Result of testing a hub's connection the way a collection reads it: its credentials Secret, the hub API, and the
 * optional Observability and Search endpoints. {@code ok} is false when any check failed; warnings do not count.
 */
public record HubConnectionTestDto(boolean ok, List<Check> checks) {

    public enum Target { CREDENTIALS, API, OBSERVABILITY, SEARCH }

    /** SKIPPED: not configured, or not tried because an earlier check failed. */
    public enum Status { OK, WARNING, FAILED, SKIPPED }

    public record Check(Target target, Status status, String message, long durationMs) {
    }

    public static HubConnectionTestDto of(List<Check> checks) {
        return new HubConnectionTestDto(checks.stream().noneMatch(check -> check.status() == Status.FAILED), checks);
    }
}
