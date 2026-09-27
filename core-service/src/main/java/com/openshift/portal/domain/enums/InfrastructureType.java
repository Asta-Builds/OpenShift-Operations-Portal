package com.openshift.portal.domain.enums;

public enum InfrastructureType {
    BARE_METAL,
    VMWARE,
    OPENSTACK,
    AWS,
    AZURE,
    GCP,
    /** Discovered cluster on a platform the portal does not model. */
    OTHER
}
