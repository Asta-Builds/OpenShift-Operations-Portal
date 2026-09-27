package com.openshift.portal.dto;

import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.domain.enums.ProviderType;
import com.openshift.portal.service.NodeCorrelationService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Where the nodes of every cluster's latest snapshot run: grouped by hypervisor cluster and host, bare-metal
 * machines, and cloud zones, with the nodes the inventory does not know listed separately.
 */
public record InfrastructureTopologyDto(
        Summary summary,
        List<HypervisorCluster> hypervisorClusters,
        List<Datacenter> bareMetal,
        List<CloudZone> cloud,
        List<Node> notInInventory,
        List<Node> noProviderId) {

    public record Summary(int nodes, int matched, int cloud, int notInInventory, int noProviderId) {
    }

    /** VMs matched to inventory rows naming their hypervisor host. */
    public record HypervisorCluster(String datacenter, String hypervisorCluster, List<Host> hosts) {
    }

    public record Host(String hypervisorHost, Integer sockets, Integer physicalCores, Integer threadsPerCore,
                       String inventorySource, List<Node> nodes) {
    }

    /** Nodes matched to inventory rows without a hypervisor: the node is the physical machine. */
    public record Datacenter(String datacenter, List<Node> nodes) {
    }

    public record CloudZone(ProviderType providerType, String zone, List<Node> nodes) {
    }

    public record Node(UUID clusterId, String clusterName, String nodeName, NodeRole role, int cpuCores,
                       BigDecimal memoryGb, ProviderType providerType, String instanceKey, String zone,
                       NodeCorrelationService.Status status, String inventorySource, Integer sockets,
                       Integer physicalCores, Integer threadsPerCore) {
    }
}
