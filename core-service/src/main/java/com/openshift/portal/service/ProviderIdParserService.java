package com.openshift.portal.service;

import com.openshift.portal.domain.enums.ProviderType;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a node's {@code spec.providerID}. It only extracts what the ID contains: the platform, a key identifying
 * the instance, and the zone for clouds. It never derives host names; which hypervisor runs a VM comes from the
 * infrastructure inventory.
 */
@Service
public class ProviderIdParserService {

    /** vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c (the VM's BIOS UUID) */
    private static final Pattern VSPHERE = Pattern.compile("vsphere://([0-9a-fA-F-]{32,36})/?");
    /** aws:///us-east-1a/i-0abc123 (older clusters: aws://us-east-1a/i-0abc123) */
    private static final Pattern AWS = Pattern.compile("aws:///?([^/]*)/(i-[0-9a-zA-Z]+)");
    /** azure:///subscriptions/<sub>/resourceGroups/<rg>/providers/Microsoft.Compute/virtualMachines/<vm>, or a scale set VM */
    private static final Pattern AZURE = Pattern.compile("azure:///?(subscriptions/.+/providers/Microsoft\\.Compute/.+)", Pattern.CASE_INSENSITIVE);
    /** gce://<project>/<zone>/<instance-name> */
    private static final Pattern GCP = Pattern.compile("gce://([^/]+)/([^/]+)/([^/]+)");
    /** openstack:///<server-uuid>, or openstack://<region>/<server-uuid> */
    private static final Pattern OPENSTACK = Pattern.compile("openstack://([^/]*)/([0-9a-fA-F-]{36})");
    /** baremetalhost:///<namespace>/<host-name>/<host-uid> */
    private static final Pattern BAREMETAL = Pattern.compile("baremetalhost:///([^/]+)/([^/]+)(?:/([^/]+))?");
    /** ovirt://<vm-uuid> */
    private static final Pattern OVIRT = Pattern.compile("ovirt://([0-9a-fA-F-]{36})");
    /** kubevirt://<vm-name> (hosted control planes / OpenShift Virtualization) */
    private static final Pattern KUBEVIRT = Pattern.compile("kubevirt://(.+)");
    /** kind://docker/<cluster>/<node> */
    private static final Pattern KIND = Pattern.compile("kind://([^/]+)/([^/]+)/([^/]+)");

    public ProviderReference parse(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return new ProviderReference(ProviderType.UNKNOWN, null, null);
        }
        String id = providerId.trim();
        Matcher m;
        if ((m = VSPHERE.matcher(id)).matches()) {
            return new ProviderReference(ProviderType.VSPHERE, normalizeKey(ProviderType.VSPHERE, m.group(1)), null);
        }
        if ((m = AWS.matcher(id)).matches()) {
            return new ProviderReference(ProviderType.AWS, m.group(2), emptyToNull(m.group(1)));
        }
        if ((m = AZURE.matcher(id)).matches()) {
            return new ProviderReference(ProviderType.AZURE, normalizeKey(ProviderType.AZURE, m.group(1)), null);
        }
        if ((m = GCP.matcher(id)).matches()) {
            return new ProviderReference(ProviderType.GCP, m.group(1) + "/" + m.group(2) + "/" + m.group(3), m.group(2));
        }
        if ((m = OPENSTACK.matcher(id)).matches()) {
            return new ProviderReference(ProviderType.OPENSTACK, normalizeKey(ProviderType.OPENSTACK, m.group(2)), emptyToNull(m.group(1)));
        }
        if ((m = BAREMETAL.matcher(id)).matches()) {
            // Host names are stable and are what an asset database knows; the uid changes if the host is re-created
            return new ProviderReference(ProviderType.BAREMETAL, m.group(1) + "/" + m.group(2), null);
        }
        if ((m = OVIRT.matcher(id)).matches()) {
            return new ProviderReference(ProviderType.OVIRT, normalizeKey(ProviderType.OVIRT, m.group(1)), null);
        }
        if ((m = KUBEVIRT.matcher(id)).matches()) {
            return new ProviderReference(ProviderType.KUBEVIRT, m.group(1), null);
        }
        if ((m = KIND.matcher(id)).matches()) {
            return new ProviderReference(ProviderType.KIND, m.group(2) + "/" + m.group(3), null);
        }
        return new ProviderReference(ProviderType.OTHER, id, null);
    }

    /**
     * Brings an instance key into the form {@link #parse} produces, so keys typed into an inventory match: UUIDs and
     * Azure resource IDs are case-insensitive, so they are lower-cased.
     */
    public static String normalizeKey(ProviderType type, String key) {
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        return switch (type) {
            case VSPHERE, AZURE, OPENSTACK, OVIRT -> trimmed.toLowerCase(Locale.ROOT);
            default -> trimmed;
        };
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * @param instanceKey identifies the VM, cloud instance or physical host within its platform; null for UNKNOWN
     * @param zone        availability zone or region when the ID carries one
     */
    public record ProviderReference(ProviderType type, String instanceKey, String zone) {
    }
}
