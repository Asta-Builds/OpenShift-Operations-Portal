package com.openshift.portal.domain.enums;

public enum Environment {
    PRODUCTION,
    STAGING,
    DEVELOPMENT,
    QA,
    /** Discovered cluster without a recognised environment label. */
    UNKNOWN
}
