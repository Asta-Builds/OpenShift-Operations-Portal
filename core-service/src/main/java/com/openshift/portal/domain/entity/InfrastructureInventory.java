package com.openshift.portal.domain.entity;

import com.openshift.portal.domain.enums.ProviderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * What an asset source knows about the machine behind one node: the hypervisor host running a VM, or the physical
 * facts of a bare-metal host. Matched to nodes on {@code providerType} and {@code instanceKey}.
 */
@Entity
@Table(name = "infrastructure_inventory", uniqueConstraints = {
    @UniqueConstraint(name = "uq_inventory_source_key", columnNames = {"source", "provider_type", "instance_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfrastructureInventory {

    public static final String SOURCE_CMDB = "CMDB";
    public static final String SOURCE_SIMULATOR = "SIMULATOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private ProviderType providerType;

    @Column(name = "instance_key", nullable = false)
    private String instanceKey;

    @Column(name = "hypervisor_host")
    private String hypervisorHost;

    @Column(name = "hypervisor_cluster")
    private String hypervisorCluster;

    @Column(name = "datacenter")
    private String datacenter;

    @Column(name = "physical_sockets")
    private Integer physicalSockets;

    @Column(name = "physical_cores")
    private Integer physicalCores;

    @Column(name = "threads_per_core")
    private Integer threadsPerCore;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}
