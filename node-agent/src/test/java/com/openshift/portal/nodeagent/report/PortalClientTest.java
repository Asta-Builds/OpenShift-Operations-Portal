package com.openshift.portal.nodeagent.report;

import com.openshift.portal.nodeagent.config.NodeAgentProperties;
import com.openshift.portal.nodeagent.node.NodeRole;
import com.openshift.portal.nodeagent.node.ReportedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortalClientTest {

    private static final String TOKEN_URI = "https://keycloak.example.com/realms/openshift-portal/protocol/openid-connect/token";
    private static final String REPORTS_URI = "https://portal.example.com/api/v1/node-reports";
    private static final String RECEIPT = "{\"clusterName\":\"prod-east-1\",\"nodes\":1,\"registered\":true}";

    private final NodeReport report = new NodeReport("prod-east-1", "1.0.0", Instant.parse("2026-09-27T18:00:00Z"),
            List.of(new ReportedNode("worker-0", NodeRole.WORKER, 16, new BigDecimal("64.00"), "vsphere://abc")));

    private NodeAgentProperties properties;
    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        properties = new NodeAgentProperties();
        properties.setClusterName("prod-east-1");
        properties.getPortal().setUrl("https://portal.example.com/api/v1/");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @Test
    void sendsTheReportWithAClientCredentialsTokenAndReusesTheToken() {
        enableAuth();
        PortalClient client = new PortalClient(restClient, new PortalTokenProvider(restClient, properties), properties);

        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "client_credentials", "client_id", "portal-node-agent", "client_secret", "s3cret")))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo(REPORTS_URI))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-1"))
                    .andExpect(jsonPath("$.clusterName").value("prod-east-1"))
                    .andExpect(jsonPath("$.collectedAt").value("2026-09-27T18:00:00Z"))
                    .andExpect(jsonPath("$.nodes[0].role").value("WORKER"))
                    .andExpect(jsonPath("$.nodes[0].cpuCores").value(16))
                    .andExpect(jsonPath("$.nodes[0].providerId").value("vsphere://abc"))
                    .andRespond(withSuccess(RECEIPT, MediaType.APPLICATION_JSON));
        }

        assertThat(client.send(report)).isEqualTo(new PortalClient.ReportReceipt("prod-east-1", 1, true));
        client.send(report);
        server.verify();
    }

    @Test
    void renewsTheTokenShortlyBeforeItExpires() {
        enableAuth();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T18:00:00Z"));
        PortalTokenProvider tokens = new PortalTokenProvider(restClient, properties, clock);
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-2\",\"expires_in\":300}", MediaType.APPLICATION_JSON));

        assertThat(tokens.token()).isEqualTo("tok-1");
        clock.now = clock.now.plusSeconds(300 - PortalTokenProvider.EXPIRY_MARGIN_SECONDS - 1);
        assertThat(tokens.token()).isEqualTo("tok-1");
        clock.now = clock.now.plusSeconds(1);
        assertThat(tokens.token()).isEqualTo("tok-2");
        server.verify();
    }

    @Test
    void rejectedTokenIsDroppedSoTheNextReportFetchesANewOne() {
        enableAuth();
        PortalClient client = new PortalClient(restClient, new PortalTokenProvider(restClient, properties), properties);
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"revoked\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(REPORTS_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"fresh\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(REPORTS_URI))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fresh"))
                .andRespond(withSuccess(RECEIPT, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.send(report)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        client.send(report);
        server.verify();
    }

    @Test
    void withoutATokenEndpointTheReportIsSentWithoutAuthorization() {
        PortalClient client = new PortalClient(restClient, new PortalTokenProvider(restClient, properties), properties);
        server.expect(requestTo(REPORTS_URI))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(RECEIPT, MediaType.APPLICATION_JSON));

        client.send(report);
        server.verify();
    }

    private void enableAuth() {
        properties.getAuth().setTokenUri(TOKEN_URI);
        properties.getAuth().setClientId("portal-node-agent");
        properties.getAuth().setClientSecret("s3cret");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
