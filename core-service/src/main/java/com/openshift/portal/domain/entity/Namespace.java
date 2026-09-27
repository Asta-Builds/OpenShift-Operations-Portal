package com.openshift.portal.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "namespaces", uniqueConstraints = {
    @UniqueConstraint(name = "uq_cluster_namespace", columnNames = {"cluster_id", "namespace_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Namespace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;

    @Column(name = "namespace_name", nullable = false)
    private String namespaceName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_team_id")
    private Team ownerTeam;

    /** Raw value of the configured owner label; kept even when it matches no team. */
    @Column(name = "owner_label_value")
    private String ownerLabelValue;

    /** Raw value of the configured cost-center label; when absent the owning team's cost center applies. */
    @Column(name = "cost_center")
    private String costCenter;

    /** False until the namespace's labels have been read once; ownership is unknown rather than missing until then. */
    @Column(name = "labels_collected", nullable = false)
    @Builder.Default
    private boolean labelsCollected = false;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /** Set when the namespace no longer exists on its cluster; cleared if it reappears. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "namespace", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NamespaceSnapshot> snapshots = new ArrayList<>();
}
