package com.openshift.portal.acm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.dto.HubConnectionTestDto;
import com.openshift.portal.dto.HubConnectionTestDto.Check;
import com.openshift.portal.dto.HubConnectionTestDto.Status;
import com.openshift.portal.dto.HubConnectionTestDto.Target;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests hubs served by a local HTTP server answering like the hub API, Observability and Search. */
class LiveHubConnectionTesterTest {

    private static final String TOKEN = "sha256~test-token";
    private static final String CPU_REQUESTS = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":["
            + "{\"metric\":{\"cluster\":\"prod-east\",\"namespace\":\"payments\"},\"value\":[1727400000.1,\"12.5\"]}]}}";
    private static final String NO_SERIES = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";

    @TempDir
    Path credentialsDir;

    private HttpServer server;
    private volatile int managedClustersStatus = 200;
    private volatile String cpuRequests = CPU_REQUESTS;
    private final AcmProperties properties = new AcmProperties();
    private LiveHubConnectionTester tester;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/apis/cluster.open-cluster-management.io/v1/managedclusters", exchange ->
                respond(exchange, managedClustersStatus, managedClustersStatus == 200
                        ? fixture("/acm/managedclusters.json")
                        : "{\"kind\":\"Status\",\"code\":" + managedClustersStatus + "}"));
        server.createContext("/api/v1/query", exchange -> respond(exchange, 200, cpuRequests));
        server.createContext("/searchapi/graphql", exchange -> respond(exchange, 200, fixture("/acm/search-namespaces.json")));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        Files.createDirectories(credentialsDir.resolve("hub-east-credentials"));
        Files.writeString(credentialsDir.resolve("hub-east-credentials/token"), TOKEN + "\n");
        properties.getAcm().setCredentialsDir(credentialsDir.toString());

        ObjectMapper objectMapper = new ObjectMapper();
        HubHttpClient http = new HubHttpClient(properties);
        HubCredentialsResolver credentials = new HubCredentialsResolver(properties);
        ObservabilityMetricsClient metrics = new ObservabilityMetricsClient(http, objectMapper);
        SearchApiClient search = new SearchApiClient(http, objectMapper);
        tester = new LiveHubConnectionTester(credentials, metrics, search, properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void reportsWhatEachEndpointAnswers() {
        HubConnectionTestDto result = tester.test(hub("hub-east-credentials", baseUrl, baseUrl + "/searchapi/graphql"));

        assertThat(result.ok()).isTrue();
        assertThat(result.checks()).extracting(Check::target)
                .containsExactly(Target.CREDENTIALS, Target.API, Target.OBSERVABILITY, Target.SEARCH);
        assertThat(check(result, Target.CREDENTIALS).message())
                .isEqualTo("Token read from Secret hub-east-credentials; no ca.crt, trusting the JVM's CAs");
        assertThat(check(result, Target.API).message()).isEqualTo("2 ManagedClusters visible, 1 available");
        assertThat(check(result, Target.OBSERVABILITY).message()).isEqualTo("CPU request series for 1 clusters");
        assertThat(check(result, Target.SEARCH).message()).isEqualTo("5 namespaces in 1 clusters");
        assertThat(result.checks()).extracting(Check::status).containsOnly(Status.OK);
    }

    @Test
    void aMissingSecretStopsBeforeCallingTheHub() {
        HubConnectionTestDto result = tester.test(hub("hub-west-credentials", baseUrl, null));

        assertThat(result.ok()).isFalse();
        assertThat(check(result, Target.CREDENTIALS).status()).isEqualTo(Status.FAILED);
        assertThat(check(result, Target.CREDENTIALS).message()).contains("Cannot read the token of ACM Hub hub-east");
        assertThat(result.checks().subList(1, 4)).extracting(Check::status).containsOnly(Status.SKIPPED);
    }

    @Test
    void aRefusedTokenFailsTheApiButTheOtherEndpointsAreStillTried() {
        managedClustersStatus = 403;

        HubConnectionTestDto result = tester.test(hub("hub-east-credentials", baseUrl, null));

        assertThat(result.ok()).isFalse();
        assertThat(check(result, Target.API).status()).isEqualTo(Status.FAILED);
        assertThat(check(result, Target.API).message()).isEqualTo("ACM Hub hub-east refused the token (HTTP 403)");
        assertThat(check(result, Target.OBSERVABILITY).status()).isEqualTo(Status.OK);
        assertThat(check(result, Target.SEARCH).status()).isEqualTo(Status.SKIPPED);
        assertThat(check(result, Target.SEARCH).message()).isEqualTo("Not configured: namespace ownership is not collected");
    }

    @Test
    void anAnswerWithoutDataIsAWarningNotAFailure() {
        cpuRequests = NO_SERIES;

        HubConnectionTestDto result = tester.test(hub("hub-east-credentials", baseUrl, null));

        assertThat(result.ok()).isTrue();
        assertThat(check(result, Target.OBSERVABILITY).status()).isEqualTo(Status.WARNING);
        assertThat(check(result, Target.OBSERVABILITY).message()).startsWith("Answered, but returned no CPU request series");
    }

    private static AcmHub hub(String secret, String observabilityUrl, String searchUrl) {
        return AcmHub.builder().name("hub-east").apiUrl(observabilityUrl).credentialsSecretRef(secret)
                .observabilityUrl(observabilityUrl).searchUrl(searchUrl).build();
    }

    private static Check check(HubConnectionTestDto result, Target target) {
        return result.checks().stream().filter(check -> check.target() == target).findFirst().orElseThrow();
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
