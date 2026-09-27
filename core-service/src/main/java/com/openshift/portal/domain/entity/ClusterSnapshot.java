package com.openshift.portal.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cluster_snapshots", indexes = {
    @Index(name = "idx_snapshots_cluster_time", columnList = "cluster_id, snapshot_timestamp DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClusterSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;

    @Column(name = "snapshot_timestamp", nullable = false)
    private LocalDateTime snapshotTimestamp;

    @Column(name = "total_cpu_cores", nullable = false)
    @Builder.Default
    private Integer totalCpuCores = 0;

    /** Requested CPU in cores, with fractions (a pod may request 250m). */
    @Column(name = "allocated_cpu_cores", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal allocatedCpuCores = BigDecimal.ZERO;

    @Column(name = "total_memory_gb", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalMemoryGb = BigDecimal.ZERO;

    @Column(name = "allocated_memory_gb", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal allocatedMemoryGb = BigDecimal.ZERO;

    @Column(name = "total_storage_gb", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalStorageGb = BigDecimal.ZERO;

    @Column(name = "allocated_storage_gb", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal allocatedStorageGb = BigDecimal.ZERO;

    @Column(name = "license_cores_count", nullable = false)
    @Builder.Default
    private Integer licenseCoresCount = 0;

    @Column(name = "total_nodes", nullable = false)
    @Builder.Default
    private Integer totalNodes = 0;

    @Column(name = "worker_nodes", nullable = false)
    @Builder.Default
    private Integer workerNodes = 0;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Whether the snapshot knows the cluster's nodes. Every running cluster has at least one node, so zero nodes means
     * none were reported (a live cluster without a fresh node agent report), and its license cores are unknown, not 0.
     */
    public boolean hasNodeData() {
        return totalNodes != null && totalNodes > 0;
    }
}
