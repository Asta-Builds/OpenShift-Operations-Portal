package com.openshift.portal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderIdParserServiceTest {

    private ProviderIdParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new ProviderIdParserService();
    }

    @Test
    void parseProviderId_vsphereUri_extractsVmwareCorrelation() {
        var info = parserService.parseProviderId("vsphere://421a7192-3bc9-8805-4f33-1996d9eb74ca");

        assertThat(info.getProviderType()).isEqualTo("VMWARE");
        assertThat(info.getInstanceId()).isEqualTo("421a7192-3bc9-8805-4f33-1996d9eb74ca");
        assertThat(info.getHypervisorHost()).startsWith("esxi-cluster-");
    }

    @Test
    void parseProviderId_awsUri_extractsZoneAndInstance() {
        var info = parserService.parseProviderId("aws:///us-east-1a/i-0a8b7c6d5e4f3a2b1");

        assertThat(info.getProviderType()).isEqualTo("AWS");
        assertThat(info.getInstanceId()).isEqualTo("i-0a8b7c6d5e4f3a2b1");
        assertThat(info.getAvailabilityZone()).isEqualTo("us-east-1a");
        assertThat(info.getHypervisorHost()).contains("aws-nitro-hypervisor-us-east-1a");
    }

    @Test
    void parseProviderId_bareMetal_extractsChassisId() {
        var info = parserService.parseProviderId("baremetal://d407ad32-f19b-4e08-9df2-bb173f4e2468");

        assertThat(info.getProviderType()).isEqualTo("BARE_METAL");
        assertThat(info.getInstanceId()).isEqualTo("d407ad32-f19b-4e08-9df2-bb173f4e2468");
        assertThat(info.getHypervisorHost()).startsWith("physical-rack-host-");
    }

    @Test
    void parseProviderId_nullOrEmpty_returnsUnknownGracefully() {
        var info = parserService.parseProviderId(null);
        assertThat(info.getProviderType()).isEqualTo("UNKNOWN");

        var infoEmpty = parserService.parseProviderId("   ");
        assertThat(infoEmpty.getProviderType()).isEqualTo("UNKNOWN");
    }
}
