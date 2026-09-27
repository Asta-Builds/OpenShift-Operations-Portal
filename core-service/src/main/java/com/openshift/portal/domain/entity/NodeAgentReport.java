package com.openshift.portal.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** The latest node report a cluster's node agent sent; each new report replaces the previous one. */
@Entity
@Table(name = "node_agent_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeAgentReport {

    @Id
    @Column(name = "cluster_name")
    private String clusterName;

    @Column(name = "agent_version")
    private String agentVersion;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "node_count", nullable = false)
    private Integer nodeCount;

    /** JSON array of {@link com.openshift.portal.acm.NodeObservation}. */
    @Column(name = "nodes", nullable = false, columnDefinition = "TEXT")
    private String nodes;
}
