package com.openshift.portal.service;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

@Service
public class ProviderIdParserService {

    public ParsedProviderInfo parseProviderId(String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) {
            return ParsedProviderInfo.builder()
                    .providerType("UNKNOWN")
                    .instanceId("unknown")
                    .hypervisorHost("unassigned")
                    .build();
        }

        String trimmed = providerId.trim();

        // 1. VMware vSphere (e.g. vsphere://421a7192-3bc9-8805-4f33-1996d9eb74ca)
        if (trimmed.startsWith("vsphere://")) {
            String uuid = trimmed.substring("vsphere://".length()).replace("/", "");
            return ParsedProviderInfo.builder()
                    .providerType("VMWARE")
                    .instanceId(uuid)
                    .hypervisorHost("esxi-cluster-" + uuid.substring(0, Math.min(4, uuid.length())))
                    .build();
        }

        // 2. AWS (e.g. aws:///us-east-1a/i-0a8b7c6d5e4f3a2b1)
        if (trimmed.startsWith("aws://")) {
            String path = trimmed.substring("aws://".length());
            String[] parts = path.split("/");
            String zone = parts.length > 1 ? parts[1] : "default-zone";
            String instance = parts.length > 2 ? parts[2] : (parts.length > 0 ? parts[parts.length - 1] : "unknown");
            return ParsedProviderInfo.builder()
                    .providerType("AWS")
                    .instanceId(instance)
                    .hypervisorHost("aws-nitro-hypervisor-" + zone)
                    .availabilityZone(zone)
                    .build();
        }

        // 3. Bare Metal (e.g. baremetal://d407ad32-f19b-4e08-9df2-bb173f4e2468)
        if (trimmed.startsWith("baremetal://")) {
            String uuid = trimmed.substring("baremetal://".length());
            return ParsedProviderInfo.builder()
                    .providerType("BARE_METAL")
                    .instanceId(uuid)
                    .hypervisorHost("physical-rack-host-" + uuid.substring(0, Math.min(4, uuid.length())))
                    .build();
        }

        // 4. Azure (e.g. azure:///subscriptions/.../virtualMachines/vm-01)
        if (trimmed.startsWith("azure://")) {
            String[] segments = trimmed.split("/");
            String vmName = segments[segments.length - 1];
            return ParsedProviderInfo.builder()
                    .providerType("AZURE")
                    .instanceId(vmName)
                    .hypervisorHost("azure-hyper-v")
                    .build();
        }

        return ParsedProviderInfo.builder()
                .providerType("CUSTOM")
                .instanceId(trimmed)
                .hypervisorHost("generic-host")
                .build();
    }

    @Data
    @Builder
    public static class ParsedProviderInfo {
        private String providerType;
        private String instanceId;
        private String hypervisorHost;
        private String availabilityZone;
    }
}
