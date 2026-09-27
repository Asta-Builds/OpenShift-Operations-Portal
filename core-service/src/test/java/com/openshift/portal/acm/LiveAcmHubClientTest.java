package com.openshift.portal.acm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.exception.AcmAccessException;
import com.openshift.portal.exception.AcmConnectionException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the live client against a local HTTP server that answers like an ACM hub API server and an ACM
 * Observability endpoint, using response shapes from the ACM documentation.
 */
class LiveAcmHubClientTest {

    private static final String TOKEN = "sha256~test-token";

    @TempDir
    Path credentialsDir;

    private HttpServer server;
    private final Map<String, String> authorizationByPath = new ConcurrentHashMap<>();
    private volatile int managedClustersStatus = 200;
    private LiveAcmHubClient client;
    private AcmHub hub;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/apis/cluster.open-cluster-management.io/v1/managedclusters", exchange -> {
            authorizationByPath.put("managedclusters", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            if (managedClustersStatus != 200) {
                respond(exchange, managedClustersStatus, "{\"kind\":\"Status\",\"code\":" + managedClustersStatus + "}");
                return;
            }
            try (InputStream fixture = getClass().getResourceAsStream("/acm/managedclusters.json")) {
                respond(exchange, 200, new String(fixture.readAllBytes(), StandardCharsets.UTF_8));
            }
        });
        server.createContext("/api/v1/query", exchange -> {
            authorizationByPath.put("query", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery().substring("query=".length()), StandardCharsets.UTF_8);
            String value = switch (query) {
                case ObservabilityMetricsClient.CPU_REQUESTS -> "30.4";
                case ObservabilityMetricsClient.MEMORY_REQUESTS -> "107374182400";   // 100 GiB
                case ObservabilityMetricsClient.PV_CAPACITY -> "1099511627776";     // 1024 GiB
                case ObservabilityMetricsClient.PVC_REQUESTS -> "536870912000";     // 500 GiB
                default -> throw new IllegalArgumentException("Unexpected query " + query);
            };
            respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":["
                    + "{\"metric\":{\"cluster\":\"prod-east\"},\"value\":[1727400000.123,\"" + value + "\"]}]}}");
        });
        server.start();

        Files.createDirectories(credentialsDir.resolve("hub-east-credentials"));
        Files.writeString(credentialsDir.resolve("hub-east-credentials/token"), TOKEN + "\n");

        AcmProperties properties = new AcmProperties();
        properties.getAcm().setCredentialsDir(credentialsDir.toString());
        client = new LiveAcmHubClient(new HubCredentialsResolver(properties),
                new ObservabilityMetricsClient(properties, new ObjectMapper()), properties);

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        hub = AcmHub.builder().name("hub-east").apiUrl(baseUrl)
                .credentialsSecretRef("hub-east-credentials").observabilityUrl(baseUrl).build();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsInventoryCapacityAndAllocationWithTheHubToken() {
        List<ClusterObservation> observations = client.fetchClusters(hub);

        assertThat(observations).extracting(ClusterObservation::clusterName).containsExactly("prod-east", "edge-lab");
        ClusterObservation prod = observations.get(0);
        assertThat(prod.isFailed()).isFalse();
        assertThat(prod.totalCpuCores()).isEqualTo(48);
        assertThat(prod.totalMemoryGb()).isEqualByComparingTo("187.34");       // 196438216 KiB
        assertThat(prod.allocatedCpuCores()).isEqualTo(30);
        assertThat(prod.allocatedMemoryGb()).isEqualByComparingTo("100.00");
        assertThat(prod.totalStorageGb()).isEqualByComparingTo("1024.00");
        assertThat(prod.allocatedStorageGb()).isEqualByComparingTo("500.00");
        assertThat(prod.metadata()).isEqualTo(new ClusterMetadata("production", "AWS", "4.14.28", "us-east-1"));
        assertThat(prod.nodes()).isEmpty();
        assertThat(prod.rawPayload()).contains("\"name\":\"prod-east\"");

        assertThat(authorizationByPath).containsEntry("managedclusters", "Bearer " + TOKEN)
                .containsEntry("query", "Bearer " + TOKEN);
    }

    @Test
    void unavailableClusterIsReportedAsFailedNotDropped() {
        ClusterObservation edge = client.fetchClusters(hub).get(1);

        assertThat(edge.isFailed()).isTrue();
        assertThat(edge.error()).contains("edge-lab").contains("not available");
    }

    @Test
    void withoutObservabilityOnlyCapacityIsCollected() {
        hub.setObservabilityUrl(null);

        ClusterObservation prod = client.fetchClusters(hub).get(0);

        assertThat(prod.totalCpuCores()).isEqualTo(48);
        assertThat(prod.allocatedCpuCores()).isZero();
        assertThat(prod.allocatedMemoryGb()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(authorizationByPath).doesNotContainKey("query");
    }

    @Test
    void refusedTokenIsNotRetryable() {
        managedClustersStatus = 403;

        assertThatThrownBy(() -> client.fetchClusters(hub)).isInstanceOf(AcmAccessException.class).hasMessageContaining("403");
    }

    @Test
    void serverErrorIsRetryable() {
        managedClustersStatus = 503;

        assertThatThrownBy(() -> client.fetchClusters(hub)).isInstanceOf(AcmConnectionException.class);
    }

    @Test
    void missingCredentialsAreNotRetryable() {
        hub.setCredentialsSecretRef("missing-secret");

        assertThatThrownBy(() -> client.fetchClusters(hub)).isInstanceOf(AcmAccessException.class)
                .hasMessageContaining("hub-east");
    }

    @Test
    void secretReferenceCannotEscapeTheCredentialsDirectory() {
        hub.setCredentialsSecretRef("../../etc");

        assertThatThrownBy(() -> client.fetchClusters(hub)).isInstanceOf(AcmAccessException.class)
                .hasMessageContaining("Invalid credentials Secret reference");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
