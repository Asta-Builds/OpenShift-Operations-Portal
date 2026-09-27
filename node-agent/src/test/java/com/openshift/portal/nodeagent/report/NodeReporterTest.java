package com.openshift.portal.nodeagent.report;

import com.openshift.portal.nodeagent.config.NodeAgentProperties;
import com.openshift.portal.nodeagent.node.NodeReader;
import com.openshift.portal.nodeagent.node.NodeRole;
import com.openshift.portal.nodeagent.node.ReportedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeReporterTest {

    private static final Instant NOW = Instant.parse("2026-09-27T18:00:00Z");

    @Mock
    private NodeReader nodeReader;
    @Mock
    private PortalClient portalClient;

    private NodeReporter reporter;

    @BeforeEach
    void setUp() {
        NodeAgentProperties properties = new NodeAgentProperties();
        properties.setClusterName("prod-east-1");
        properties.setVersion("1.2.3");
        reporter = new NodeReporter(nodeReader, portalClient, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void healthIsUnknownBeforeTheFirstReport() {
        assertThat(reporter.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void sendsAllNodesOfTheClusterAndReportsUp() {
        List<ReportedNode> nodes = List.of(
                new ReportedNode("master-0", NodeRole.MASTER, 8, new BigDecimal("32.00"), null),
                new ReportedNode("worker-0", NodeRole.WORKER, 16, new BigDecimal("64.00"), "vsphere://abc"));
        when(nodeReader.readNodes()).thenReturn(nodes);
        when(portalClient.send(any())).thenReturn(new PortalClient.ReportReceipt("prod-east-1", 2, true));

        reporter.report();

        ArgumentCaptor<NodeReport> sent = ArgumentCaptor.forClass(NodeReport.class);
        verify(portalClient).send(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new NodeReport("prod-east-1", "1.2.3", NOW, nodes));
        Health health = reporter.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("nodes", 2).containsEntry("lastSuccess", NOW.toString());
    }

    @Test
    void failedReportIsDownUntilTheNextOneSucceeds() {
        when(nodeReader.readNodes()).thenReturn(List.of());
        when(portalClient.send(any()))
                .thenThrow(new ResourceAccessException("I/O error on POST request: Connection refused"))
                .thenReturn(new PortalClient.ReportReceipt("prod-east-1", 0, false));

        reporter.report();
        assertThat(reporter.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(reporter.health().getDetails()).containsEntry("error", "I/O error on POST request: Connection refused");

        reporter.report();
        assertThat(reporter.health().getStatus()).isEqualTo(Status.UP);
    }
}
