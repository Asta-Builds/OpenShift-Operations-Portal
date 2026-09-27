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

    @Column(name = "cost_center")
    private String costCenter;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "namespace", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NamespaceSnapshot> snapshots = new ArrayList<>();
}
