package com.openshift.portal.service;

import com.openshift.portal.acm.ClusterMetadata;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterDiscoveryTest {

    private final ClusterDiscovery discovery = new ClusterDiscovery();

    @Test
    void registersDiscoveredClusterFromHubMetadata() {
        AcmHub hub = AcmHub.builder().name("hub-east").build();

        Cluster cluster = discovery.newCluster(hub, "prod-east", new ClusterMetadata("prod", "VSphere", "4.14.28", "dc-1"));

        assertThat(cluster.getAcmHub()).isSameAs(hub);
        assertThat(cluster.getClusterName()).isEqualTo("prod-east");
        assertThat(cluster.getEnvironment()).isEqualTo(Environment.PRODUCTION);
        assertThat(cluster.getInfrastructureType()).isEqualTo(InfrastructureType.VMWARE);
        assertThat(cluster.getOpenshiftVersion()).isEqualTo("4.14.28");
        assertThat(cluster.getRegion()).isEqualTo("dc-1");
    }

    @Test
    void unrecognisedValuesAreMarkedRatherThanGuessed() {
        Cluster cluster = discovery.newCluster(AcmHub.builder().build(), "lab", new ClusterMetadata("sandbox", "IBM", null, null));

        assertThat(cluster.getEnvironment()).isEqualTo(Environment.UNKNOWN);
        assertThat(cluster.getInfrastructureType()).isEqualTo(InfrastructureType.OTHER);
        assertThat(ClusterDiscovery.environment(null)).isEqualTo(Environment.UNKNOWN);
        assertThat(ClusterDiscovery.infrastructure(null)).isEqualTo(InfrastructureType.OTHER);
    }

    @Test
    void refreshAppliesUpgradedVersionOnly() {
        Cluster cluster = Cluster.builder().openshiftVersion("4.14.28").region("us-east-1").build();

        assertThat(discovery.refresh(cluster, new ClusterMetadata(null, null, "4.14.28", "us-east-1"))).isFalse();
        assertThat(discovery.refresh(cluster, new ClusterMetadata(null, null, "4.15.2", null))).isTrue();
        assertThat(cluster.getOpenshiftVersion()).isEqualTo("4.15.2");
        assertThat(cluster.getRegion()).isEqualTo("us-east-1");
    }
}
