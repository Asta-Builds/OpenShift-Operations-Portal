package com.openshift.portal.nodeagent.node;

/** Same names as the portal's node roles; only WORKER nodes count towards license cores. */
public enum NodeRole {
    WORKER,
    MASTER,
    INFRA
}
