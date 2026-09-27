package com.openshift.portal.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An owner label value that maps to a team besides the team's own name, for example a legacy or abbreviated
 * value. Stored in the normalized form used for matching (see {@code OwnerResolver}).
 */
@Entity
@Table(name = "team_aliases", uniqueConstraints = {
    @UniqueConstraint(name = "uq_team_alias", columnNames = {"alias"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String alias;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
