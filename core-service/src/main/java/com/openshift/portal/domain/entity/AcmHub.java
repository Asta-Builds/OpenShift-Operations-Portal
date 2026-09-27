package com.openshift.portal.domain.entity;

import com.openshift.portal.domain.enums.HubStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "acm_hubs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcmHub {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "api_url", nullable = false, length = 512)
    private String apiUrl;

    @Column(name = "auth_token", columnDefinition = "TEXT")
    private String authToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private HubStatus status = HubStatus.ACTIVE;

    @Column(name = "last_sync_timestamp")
    private LocalDateTime lastSyncTimestamp;

    /** Collection cycles in a row that got no cluster data from this hub; reset by any successful read. */
    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "acmHub", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Cluster> clusters = new ArrayList<>();
}
