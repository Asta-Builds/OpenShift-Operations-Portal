package com.openshift.portal.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "namespace_snapshots", indexes = {
    @Index(name = "idx_ns_snapshot_time", columnList = "namespace_id, snapshot_timestamp DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NamespaceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "namespace_id", nullable = false)
    private Namespace namespace;

    /** The cluster snapshot collected together with this one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_snapshot_id")
    private ClusterSnapshot clusterSnapshot;

    @Column(name = "snapshot_timestamp", nullable = false)
    private LocalDateTime snapshotTimestamp;

    @Column(name = "cpu_request_cores", precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal cpuRequestCores = BigDecimal.ZERO;

    @Column(name = "cpu_limit_cores", precision = 8, scale = 2)
    private BigDecimal cpuLimitCores;

    @Column(name = "memory_request_gb", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal memoryRequestGb = BigDecimal.ZERO;

    @Column(name = "memory_limit_gb", precision = 10, scale = 2)
    private BigDecimal memoryLimitGb;

    /** Actual consumption; null when the source does not provide it. */
    @Column(name = "cpu_usage_cores", precision = 8, scale = 2)
    private BigDecimal cpuUsageCores;

    @Column(name = "memory_usage_gb", precision = 10, scale = 2)
    private BigDecimal memoryUsageGb;

    @Column(name = "pvc_request_gb", precision = 12, scale = 2)
    private BigDecimal pvcRequestGb;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
