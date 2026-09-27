package com.openshift.portal.domain.enums;

/** Platform named by a node's {@code spec.providerID} scheme. */
public enum ProviderType {
    VSPHERE,
    AWS,
    AZURE,
    GCP,
    OPENSTACK,
    /** Metal3 BareMetalHost (OpenShift IPI bare metal): the node is the physical machine. */
    BAREMETAL,
    OVIRT,
    KUBEVIRT,
    /** kind clusters in Docker, used by the hub lab. */
    KIND,
    /** A scheme this parser does not know; the raw providerID is the instance key. */
    OTHER,
    /** No providerID, typical of UPI and platform "none" installs. */
    UNKNOWN;

    /** Public clouds own the hardware, so there is no hypervisor host to correlate. */
    public boolean isCloud() {
        return this == AWS || this == AZURE || this == GCP;
    }
}
