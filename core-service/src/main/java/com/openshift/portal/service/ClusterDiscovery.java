package com.openshift.portal.service;

import com.openshift.portal.acm.ClusterMetadata;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * Turns what a hub reports about a managed cluster into the portal's cluster record. Values the portal cannot
 * recognise become UNKNOWN / OTHER rather than a guess.
 */
@Component
public class ClusterDiscovery {

    public Cluster newCluster(AcmHub hub, String name, ClusterMetadata metadata) {
        return Cluster.builder()
                .acmHub(hub)
                .clusterName(name)
                .environment(environment(metadata.environment()))
                .infrastructureType(infrastructure(metadata.platform()))
                .openshiftVersion(metadata.openshiftVersion())
                .region(metadata.region())
                .build();
    }

    /** Applies changed version and region; returns whether anything changed. */
    public boolean refresh(Cluster cluster, ClusterMetadata metadata) {
        boolean changed = false;
        if (metadata.openshiftVersion() != null && !Objects.equals(metadata.openshiftVersion(), cluster.getOpenshiftVersion())) {
            cluster.setOpenshiftVersion(metadata.openshiftVersion());
            changed = true;
        }
        if (metadata.region() != null && !Objects.equals(metadata.region(), cluster.getRegion())) {
            cluster.setRegion(metadata.region());
            changed = true;
        }
        return changed;
    }

    /** The cluster's {@code environment} label: prod/production, stage/staging, dev/development, qa/test. */
    static Environment environment(String label) {
        if (label == null) {
            return Environment.UNKNOWN;
        }
        return switch (label.trim().toLowerCase(Locale.ROOT)) {
            case "prod", "production" -> Environment.PRODUCTION;
            case "stage", "staging" -> Environment.STAGING;
            case "dev", "development" -> Environment.DEVELOPMENT;
            case "qa", "test" -> Environment.QA;
            default -> Environment.UNKNOWN;
        };
    }

    /** ClusterClaim {@code platform.open-cluster-management.io}, e.g. AWS, Azure, GCP, VSphere, OpenStack, BareMetal. */
    static InfrastructureType infrastructure(String platform) {
        if (platform == null) {
            return InfrastructureType.OTHER;
        }
        return switch (platform.trim().toLowerCase(Locale.ROOT)) {
            case "aws" -> InfrastructureType.AWS;
            case "azure" -> InfrastructureType.AZURE;
            case "gcp" -> InfrastructureType.GCP;
            case "vsphere" -> InfrastructureType.VMWARE;
            case "openstack" -> InfrastructureType.OPENSTACK;
            case "baremetal" -> InfrastructureType.BARE_METAL;
            default -> InfrastructureType.OTHER;
        };
    }
}
