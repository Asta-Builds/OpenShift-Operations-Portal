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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the live client against a local HTTP server that answers like an ACM hub API server, an ACM Observability
 * endpoint and the ACM Search API, using response shapes from the ACM documentation.
 */
class LiveAcmHubClientTest {

    private static final String TOKEN = "sha256~test-token";
    private static final long GIB = 1024L * 1024 * 1024;

    @TempDir
    Path credentialsDir;

    private HttpServer server;
    private final Map<String, String> authorizationByPath = new ConcurrentHashMap<>();
    private final Map<String, String> requestBodies = new ConcurrentHashMap<>();
    private volatile int managedClustersStatus = 200;
    private volatile int searchStatus = 200;
    private final AcmProperties properties = new AcmProperties();
    private volatile Map<String, String> promQlAnswers;
    private LiveAcmHubClient client;
    private AcmHub hub;

    @BeforeEach
    void setUp() throws IOException {
        AcmProperties.Queries queries = properties.getAcm().getQueries();
        // Namespace series of prod-east; kube-system has requests but no owner label, idle has no pods at all
        promQlAnswers = Map.of(
                queries.getNamespaceCpuRequests(), namespaceVector(Map.of(
                        "payments", "12.5", "web", "10.25", "batch", "4", "kube-system", "3.65")),
                queries.getNamespaceMemoryRequests(), namespaceVector(Map.of(
                        "payments", String.valueOf(40 * GIB), "web", String.valueOf(30 * GIB),
                        "batch", String.valueOf(20 * GIB), "kube-system", String.valueOf(10 * GIB))),
                queries.getNamespaceCpuUsage(), namespaceVector(Map.of("payments", "6.2", "web", "3.1", "batch", "NaN")),
                queries.getNamespaceMemoryUsage(), namespaceVector(Map.of("payments", String.valueOf(25 * GIB))),
                queries.getNamespacePvcRequests(), namespaceVector(Map.of(
                        "payments", String.valueOf(300 * GIB), "web", String.valueOf(200 * GIB))),
                queries.getClusterPvCapacity(), vector("{\"cluster\":\"prod-east\"}", String.valueOf(1024 * GIB)));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/apis/cluster.open-cluster-management.io/v1/managedclusters", exchange -> {
            authorizationByPath.put("managedclusters", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            if (managedClustersStatus != 200) {
                respond(exchange, managedClustersStatus, "{\"kind\":\"Status\",\"code\":" + managedClustersStatus + "}");
                return;
            }
            respond(exchange, 200, fixture("/acm/managedclusters.json"));
        });
        server.createContext("/api/v1/query", exchange -> {
            authorizationByPath.put("query", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery().substring("query=".length()), StandardCharsets.UTF_8);
            String answer = promQlAnswers.get(query);
            if (answer == null) {
                respond(exchange, 400, "{\"status\":\"error\",\"error\":\"unexpected query " + query + "\"}");
                return;
            }
            respond(exchange, 200, answer);
        });
        server.createContext("/searchapi/graphql", exchange -> {
            authorizationByPath.put("search", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            requestBodies.put("search", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (searchStatus != 200) {
                respond(exchange, searchStatus, "{\"message\":\"search unavailable\"}");
                return;
            }
            respond(exchange, 200, fixture("/acm/search-namespaces.json"));
        });
        server.start();

        Files.createDirectories(credentialsDir.resolve("hub-east-credentials"));
        Files.writeString(credentialsDir.resolve("hub-east-credentials/token"), TOKEN + "\n");

        properties.getAcm().setCredentialsDir(credentialsDir.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        HubHttpClient http = new HubHttpClient(properties);
        client = new LiveAcmHubClient(new HubCredentialsResolver(properties),
                new ObservabilityMetricsClient(http, objectMapper), new SearchApiClient(http, objectMapper), properties);

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        hub = AcmHub.builder().name("hub-east").apiUrl(baseUrl)
                .credentialsSecretRef("hub-east-credentials").observabilityUrl(baseUrl)
                .searchUrl(baseUrl + "/searchapi/graphql").build();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsInventoryCapacityAndRequestsWithTheHubToken() {
        List<ClusterObservation> observations = client.fetchClusters(hub);

        assertThat(observations).extracting(ClusterObservation::clusterName).containsExactly("prod-east", "edge-lab");
        ClusterObservation prod = observations.get(0);
        assertThat(prod.isFailed()).isFalse();
        assertThat(prod.totalCpuCores()).isEqualTo(48);
        assertThat(prod.totalMemoryGb()).isEqualByComparingTo("187.34");       // 196438216 KiB
        assertThat(prod.totalStorageGb()).isEqualByComparingTo("1024.00");
        assertThat(prod.metadata()).isEqualTo(new ClusterMetadata("production", "AWS", "4.14.28", "us-east-1"));
        assertThat(prod.nodes()).isEmpty();
        assertThat(prod.rawPayload()).contains("\"name\":\"prod-east\"");

        assertThat(authorizationByPath).containsEntry("managedclusters", "Bearer " + TOKEN)
                .containsEntry("query", "Bearer " + TOKEN)
                .containsEntry("search", "Bearer " + TOKEN);
    }

    @Test
    void clusterRequestsAreTheSumOfItsNamespaces() {
        ClusterObservation prod = client.fetchClusters(hub).get(0);

        BigDecimal namespaceCpu = prod.namespaces().namespaces().stream()
                .map(NamespaceObservation::cpuRequestCores).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(namespaceCpu).isEqualByComparingTo("30.40");
        assertThat(prod.allocatedCpuCores()).isEqualByComparingTo("30.40");
        assertThat(prod.allocatedMemoryGb()).isEqualByComparingTo("100.00");
        assertThat(prod.allocatedStorageGb()).isEqualByComparingTo("500.00");
    }

    @Test
    void namespacesCarryLabelsFromSearchAndValuesFromMetrics() {
        NamespaceInventory inventory = client.fetchClusters(hub).get(0).namespaces();

        assertThat(inventory.complete()).isTrue();
        Map<String, NamespaceObservation> byName = inventory.namespaces().stream()
                .collect(Collectors.toMap(NamespaceObservation::name, ns -> ns));
        assertThat(byName).containsOnlyKeys("payments", "web", "batch", "kube-system", "idle");

        NamespaceObservation payments = byName.get("payments");
        assertThat(payments.labels()).containsEntry("openshift.io/owner-team", "payments-platform")
                .containsEntry("cost-center", "CC-FIN-104");
        assertThat(payments.cpuRequestCores()).isEqualByComparingTo("12.50");
        assertThat(payments.memoryRequestGb()).isEqualByComparingTo("40.00");
        assertThat(payments.cpuUsageCores()).isEqualByComparingTo("6.20");
        assertThat(payments.memoryUsageGb()).isEqualByComparingTo("25.00");
        assertThat(payments.pvcRequestGb()).isEqualByComparingTo("300.00");

        // A NaN sample and a missing series both mean "not known"
        assertThat(byName.get("batch").cpuUsageCores()).isNull();
        assertThat(byName.get("kube-system").labels()).doesNotContainKey("openshift.io/owner-team");
        // Known to Search but without pods: listed, with nothing requested
        assertThat(byName.get("idle").cpuRequestCores()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(byName.get("idle").pvcRequestGb()).isNull();

        assertThat(requestBodies.get("search")).contains("\"property\":\"kind\"").contains("\"Namespace\"");
    }

    @Test
    void failingSearchKeepsCollectingWithoutLabels() {
        searchStatus = 503;

        ClusterObservation prod = client.fetchClusters(hub).get(0);

        assertThat(prod.isFailed()).isFalse();
        assertThat(prod.allocatedCpuCores()).isEqualByComparingTo("30.40");
        assertThat(prod.namespaces().complete()).isFalse();
        assertThat(prod.namespaces().namespaces()).allSatisfy(ns -> assertThat(ns.labels()).isNull());
        // Without Search only namespaces that have series are known
        assertThat(prod.namespaces().namespaces()).extracting(NamespaceObservation::name)
                .containsExactly("batch", "kube-system", "payments", "web");
    }

    @Test
    void withoutSearchNamespacesComeFromMetricsOnly() {
        hub.setSearchUrl(null);

        NamespaceInventory inventory = client.fetchClusters(hub).get(0).namespaces();

        assertThat(inventory.complete()).isFalse();
        assertThat(inventory.namespaces()).hasSize(4).allSatisfy(ns -> assertThat(ns.labels()).isNull());
        assertThat(authorizationByPath).doesNotContainKey("search");
    }

    @Test
    void clusterWithoutRequestSeriesIsFailedRatherThanZero() {
        // Observability answers, but its series for prod-east are missing (scrape gap, collector down)
        Map<String, String> answers = new java.util.HashMap<>(promQlAnswers);
        answers.put(properties.getAcm().getQueries().getNamespaceCpuRequests(), vector(""));
        promQlAnswers = answers;

        ClusterObservation prod = client.fetchClusters(hub).get(0);

        assertThat(prod.isFailed()).isTrue();
        assertThat(prod.error()).contains("no request series").contains("prod-east");
    }

    @Test
    void unavailableClusterIsReportedAsFailedNotDropped() {
        ClusterObservation edge = client.fetchClusters(hub).get(1);

        assertThat(edge.isFailed()).isTrue();
        assertThat(edge.error()).contains("edge-lab").contains("not available");
    }

    @Test
    void withoutObservabilityOnlyCapacityAndLabelsAreCollected() {
        hub.setObservabilityUrl(null);

        ClusterObservation prod = client.fetchClusters(hub).get(0);

        assertThat(prod.totalCpuCores()).isEqualTo(48);
        assertThat(prod.allocatedCpuCores()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(prod.allocatedMemoryGb()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(prod.namespaces().namespaces()).hasSize(5)
                .allSatisfy(ns -> assertThat(ns.cpuUsageCores()).isNull());
        assertThat(authorizationByPath).doesNotContainKey("query");
    }

    @Test
    void withNeitherEndpointNoNamespacesAreReported() {
        hub.setObservabilityUrl(null);
        hub.setSearchUrl(null);

        assertThat(client.fetchClusters(hub).get(0).namespaces()).isNull();
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

    @Test
    void searchLabelStringsAreSplitIntoPairs() {
        assertThat(SearchApiClient.parseLabels("a=1; b=x=y;  c = 3 ;broken"))
                .containsExactly(Map.entry("a", "1"), Map.entry("b", "x=y"), Map.entry("c", "3"));
        assertThat(SearchApiClient.parseLabels("")).isEmpty();
    }

    private static String namespaceVector(Map<String, String> valuesByNamespace) {
        return vector(valuesByNamespace.entrySet().stream()
                .map(e -> "{\"metric\":{\"cluster\":\"prod-east\",\"namespace\":\"" + e.getKey()
                        + "\"},\"value\":[1727400000.123,\"" + e.getValue() + "\"]}")
                .collect(Collectors.joining(",")));
    }

    private static String vector(String metric, String value) {
        return vector("{\"metric\":" + metric + ",\"value\":[1727400000.123,\"" + value + "\"]}");
    }

    private static String vector(String series) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[" + series + "]}}";
    }

    private String fixture(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
