package com.openshift.portal.domain.entity;

import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.domain.enums.ProviderType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "node_metrics_snapshots", indexes = {
    @Index(name = "idx_node_snapshots_time", columnList = "cluster_id, snapshot_timestamp DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeMetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id")
    private ClusterSnapshot snapshot;

    @Column(name = "snapshot_timestamp", nullable = false)
    private LocalDateTime snapshotTimestamp;

    @Column(name = "node_name", nullable = false)
    private String nodeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeRole role;

    @Column(name = "host_type")
    @Builder.Default
    private String hostType = "VIRTUAL";

    @Column(name = "cpu_cores", nullable = false)
    @Builder.Default
    private Integer cpuCores = 0;

    @Column(name = "memory_gb", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal memoryGb = BigDecimal.ZERO;

    /** The instance key parsed from the providerID (VM UUID, cloud instance id, BareMetalHost name, ...). */
    @Column(name = "underlying_host_id")
    private String underlyingHostId;

    @Column(name = "provider_id")
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", length = 30)
    private ProviderType providerType;

    /** Availability zone or region, when the providerID carries one. */
    @Column(name = "provider_zone", length = 100)
    private String providerZone;

    // Filled only from a matched infrastructure inventory row; null when the machine is not in the inventory

    @Column(name = "inventory_source", length = 30)
    private String inventorySource;

    @Column(name = "hypervisor_host")
    private String hypervisorHost;

    @Column(name = "hypervisor_cluster")
    private String hypervisorCluster;

    @Column(name = "datacenter")
    private String datacenter;

    /** Of the machine running the node: the hypervisor host for a VM, the node itself on bare metal. */
    @Column(name = "sockets")
    private Integer sockets;

    @Column(name = "physical_cores")
    private Integer physicalCores;

    @Column(name = "threads_per_core")
    private Integer threadsPerCore;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
