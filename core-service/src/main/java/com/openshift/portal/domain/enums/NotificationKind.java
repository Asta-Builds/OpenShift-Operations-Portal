package com.openshift.portal.domain.enums;

public enum NotificationKind {
    SCHEDULED_REPORT,
    /** The license high watermark exceeds the contracted cap. */
    LICENSE_BREACH,
    /** Projected CPU or memory exhaustion within the configured runway. */
    CAPACITY_RUNWAY
}
