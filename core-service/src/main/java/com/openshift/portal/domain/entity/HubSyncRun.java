package com.openshift.portal.domain.entity;

import com.openshift.portal.domain.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Outcome of one collection cycle for one ACM hub.
 */
@Entity
@Table(name = "hub_sync_runs", indexes = {
    @Index(name = "idx_hub_sync_runs_hub_time", columnList = "hub_id, started_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HubSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id", nullable = false)
    private AcmHub hub;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SyncStatus status;

    /** Calls actually made to the hub, including retries; 0 when the circuit breaker was open. */
    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "clusters_ok", nullable = false)
    @Builder.Default
    private Integer clustersOk = 0;

    @Column(name = "clusters_failed", nullable = false)
    @Builder.Default
    private Integer clustersFailed = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
