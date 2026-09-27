package com.openshift.portal.service;

import com.openshift.portal.domain.enums.ProviderType;
import com.openshift.portal.service.ProviderIdParserService.ProviderReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** providerIDs in the formats each platform's cloud provider writes into {@code Node.spec.providerID}. */
class ProviderIdParserServiceTest {

    private final ProviderIdParserService parser = new ProviderIdParserService();

    static Stream<Arguments> realProviderIds() {
        return Stream.of(
                // vSphere: the VM's BIOS UUID, lower-cased so inventory keys match regardless of case
                arguments("vsphere://4237C5F4-2A4B-D3C9-1B6E-6E1F2D3A4B5C", ProviderType.VSPHERE,
                        "4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c", null),
                // AWS: zone and instance id, current and legacy form
                arguments("aws:///us-east-1a/i-0a8b7c6d5e4f3a2b1", ProviderType.AWS, "i-0a8b7c6d5e4f3a2b1", "us-east-1a"),
                arguments("aws://eu-west-1b/i-0123456789abcdef0", ProviderType.AWS, "i-0123456789abcdef0", "eu-west-1b"),
                // Azure: the VM's resource id, case-insensitive; a scale set VM too
                arguments("azure:///subscriptions/0a1b2c3d-1111-2222-3333-444455556666/resourceGroups/ocp-prod-RG/providers/Microsoft.Compute/virtualMachines/ocp-prod-worker-eastus1-abcde",
                        ProviderType.AZURE,
                        "subscriptions/0a1b2c3d-1111-2222-3333-444455556666/resourcegroups/ocp-prod-rg/providers/microsoft.compute/virtualmachines/ocp-prod-worker-eastus1-abcde",
                        null),
                arguments("azure:///subscriptions/sub/resourceGroups/rg/providers/Microsoft.Compute/virtualMachineScaleSets/workers/virtualMachines/3",
                        ProviderType.AZURE,
                        "subscriptions/sub/resourcegroups/rg/providers/microsoft.compute/virtualmachinescalesets/workers/virtualmachines/3",
                        null),
                // GCP: project, zone and instance name
                arguments("gce://my-project-123/europe-west1-b/ocp-prod-abcde-worker-b-xyz12", ProviderType.GCP,
                        "my-project-123/europe-west1-b/ocp-prod-abcde-worker-b-xyz12", "europe-west1-b"),
                // OpenStack: server UUID, with and without region
                arguments("openstack:///8a7c6d5e-4f3a-2b1c-0d9e-8f7a6b5c4d3e", ProviderType.OPENSTACK,
                        "8a7c6d5e-4f3a-2b1c-0d9e-8f7a6b5c4d3e", null),
                arguments("openstack://RegionOne/8A7C6D5E-4F3A-2B1C-0D9E-8F7A6B5C4D3E", ProviderType.OPENSTACK,
                        "8a7c6d5e-4f3a-2b1c-0d9e-8f7a6b5c4d3e", "RegionOne"),
                // Metal3 BareMetalHost: namespace/host, which an asset database knows; the uid is ignored
                arguments("baremetalhost:///openshift-machine-api/worker-0/5d1a8b3c-7e2f-4a6b-9c0d-1e2f3a4b5c6d",
                        ProviderType.BAREMETAL, "openshift-machine-api/worker-0", null),
                arguments("ovirt://2f8d4e6a-1b3c-4d5e-8f9a-0b1c2d3e4f5a", ProviderType.OVIRT,
                        "2f8d4e6a-1b3c-4d5e-8f9a-0b1c2d3e4f5a", null),
                arguments("kubevirt://hosted-worker-abcde", ProviderType.KUBEVIRT, "hosted-worker-abcde", null),
                // kind, as the hub lab's nodes report it
                arguments("kind://docker/cluster1/cluster1-control-plane", ProviderType.KIND, "cluster1/cluster1-control-plane", null),
                // Unknown scheme: kept whole, never interpreted
                arguments("ibm://account/us-south/us-south-1/cluster/worker-1", ProviderType.OTHER,
                        "ibm://account/us-south/us-south-1/cluster/worker-1", null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("realProviderIds")
    void readsPlatformInstanceAndZone(String providerId, ProviderType type, String instanceKey, String zone) {
        assertThat(parser.parse(providerId)).isEqualTo(new ProviderReference(type, instanceKey, zone));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingProviderIdIsUnknown(String providerId) {
        assertThat(parser.parse(providerId)).isEqualTo(new ProviderReference(ProviderType.UNKNOWN, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vsphere://not-a-uuid", "aws:///us-east-1a/", "gce://project-only", "baremetal://d407ad32"})
    void malformedIdsAreNotForcedIntoAPlatform(String providerId) {
        assertThat(parser.parse(providerId).type()).isEqualTo(ProviderType.OTHER);
        assertThat(parser.parse(providerId).instanceKey()).isEqualTo(providerId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  4237C5F4-2A4B-D3C9-1B6E-6E1F2D3A4B5C  "})
    void inventoryKeysAreNormalizedLikeParsedOnes(String key) {
        assertThat(ProviderIdParserService.normalizeKey(ProviderType.VSPHERE, key))
                .isEqualTo(parser.parse("vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c").instanceKey());
    }
}
