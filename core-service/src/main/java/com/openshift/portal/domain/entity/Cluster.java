package com.openshift.portal.domain.entity;

import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clusters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cluster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acm_hub_id")
    private AcmHub acmHub;

    @Column(name = "cluster_name", nullable = false, unique = true)
    private String clusterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_team_id")
    private Team ownerTeam;

    @Enumerated(EnumType.STRING)
    @Column(name = "infrastructure_type")
    @Builder.Default
    private InfrastructureType infrastructureType = InfrastructureType.BARE_METAL;

    @Column(name = "openshift_version")
    private String openshiftVersion;

    @Column(name = "region")
    private String region;

    @Column(name = "status")
    @Builder.Default
    private String status = "READY";

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClusterSnapshot> snapshots = new ArrayList<>();

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NodeMetricsSnapshot> nodeMetrics = new ArrayList<>();

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Namespace> namespaces = new ArrayList<>();
}
