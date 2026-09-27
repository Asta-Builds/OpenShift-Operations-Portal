package com.openshift.portal.nodeagent.report;

import com.openshift.portal.nodeagent.config.NodeAgentProperties;
import com.openshift.portal.nodeagent.node.NodeReader;
import com.openshift.portal.nodeagent.node.ReportedNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Reads the nodes and sends them to the portal on a fixed delay. A failed report is logged and retried at the next
 * interval; the portal keeps using the last report it received until that report is too old.
 */
@Component("nodeReport")
@Slf4j
public class NodeReporter implements HealthIndicator {

    private final NodeReader nodeReader;
    private final PortalClient portalClient;
    private final NodeAgentProperties properties;
    private final Clock clock;

    private volatile Instant lastSuccess;
    private volatile Integer lastNodeCount;
    private volatile String lastError;

    @Autowired
    public NodeReporter(NodeReader nodeReader, PortalClient portalClient, NodeAgentProperties properties) {
        this(nodeReader, portalClient, properties, Clock.systemUTC());
    }

    NodeReporter(NodeReader nodeReader, PortalClient portalClient, NodeAgentProperties properties, Clock clock) {
        this.nodeReader = nodeReader;
        this.portalClient = portalClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${node-agent.initial-delay:PT10S}", fixedDelayString = "${node-agent.report-interval:PT5M}")
    public void report() {
        try {
            List<ReportedNode> nodes = nodeReader.readNodes();
            NodeReport report = new NodeReport(properties.getClusterName(), properties.getVersion(), clock.instant(), nodes);
            PortalClient.ReportReceipt receipt = portalClient.send(report);
            lastSuccess = clock.instant();
            lastNodeCount = nodes.size();
            lastError = null;
            if (receipt != null && !receipt.registered()) {
                log.warn("Reported {} nodes, but no ACM hub has reported cluster {} to the portal yet; check that "
                        + "node-agent.cluster-name matches the ManagedCluster name", nodes.size(), properties.getClusterName());
            } else {
                log.info("Reported {} nodes of cluster {} to the portal", nodes.size(), properties.getClusterName());
            }
        } catch (Exception e) {
            lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Node report for cluster {} failed, retrying in {}: {}", properties.getClusterName(),
                    properties.getReportInterval(), lastError);
        }
    }

    /**
     * Shown under /actuator/health; the Kubernetes probes use the liveness and readiness groups, which leave it
     * out, so an unreachable portal never restarts the agent.
     */
    @Override
    public Health health() {
        Health.Builder health;
        if (lastError != null) {
            health = Health.down().withDetail("error", lastError);
        } else if (lastSuccess != null) {
            health = Health.up();
        } else {
            health = Health.unknown();
        }
        if (lastSuccess != null) {
            health.withDetail("lastSuccess", lastSuccess.toString()).withDetail("nodes", lastNodeCount);
        }
        return health.withDetail("cluster", properties.getClusterName()).build();
    }
}
