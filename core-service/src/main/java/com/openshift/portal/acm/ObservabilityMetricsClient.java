package com.openshift.portal.acm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.exception.AcmAccessException;
import com.openshift.portal.exception.AcmConnectionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Instant PromQL queries against an ACM Observability endpoint (the Thanos querier behind the
 * {@code rbac-query-proxy} route). Every query sums by the {@code cluster} label ACM adds to federated series.
 * Metrics missing from the hub's allowlist simply return no series.
 */
@Component
@RequiredArgsConstructor
public class ObservabilityMetricsClient {

    public static final String CPU_REQUESTS = "sum by (cluster) (kube_pod_container_resource_requests{resource=\"cpu\"})";
    public static final String MEMORY_REQUESTS = "sum by (cluster) (kube_pod_container_resource_requests{resource=\"memory\"})";
    public static final String PV_CAPACITY = "sum by (cluster) (kube_persistentvolume_capacity_bytes)";
    public static final String PVC_REQUESTS = "sum by (cluster) (kube_persistentvolumeclaim_resource_requests_storage_bytes)";

    private final AcmProperties properties;
    private final ObjectMapper objectMapper;

    /** Value per cluster name for one query. */
    public Map<String, BigDecimal> query(String observabilityUrl, HubCredentialsResolver.HubCredentials credentials,
                                         String promQl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(observabilityUrl.replaceAll("/+$", "") + "/api/v1/query?query="
                        + URLEncoder.encode(promQl, StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + credentials.token())
                .timeout(Duration.ofMillis(properties.getCollector().getReadTimeoutMs()))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient(credentials).send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AcmConnectionException("Observability query to " + observabilityUrl + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AcmConnectionException("Observability query to " + observabilityUrl + " was interrupted", e);
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new AcmAccessException("Observability at " + observabilityUrl + " refused the token (HTTP " + status + ")");
        }
        if (status >= 500) {
            throw new AcmConnectionException("Observability at " + observabilityUrl + " answered HTTP " + status);
        }
        if (status != 200) {
            throw new IllegalStateException("Observability at " + observabilityUrl + " rejected the query (HTTP " + status + ")");
        }
        return parseVector(response.body());
    }

    Map<String, BigDecimal> parseVector(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"success".equals(root.path("status").asText())) {
                throw new IllegalStateException("PromQL query failed: " + root.path("error").asText("unknown error"));
            }
            Map<String, BigDecimal> values = new HashMap<>();
            for (JsonNode series : root.path("data").path("result")) {
                String cluster = series.path("metric").path("cluster").asText(null);
                JsonNode value = series.path("value");
                if (cluster != null && value.size() == 2) {
                    values.put(cluster, new BigDecimal(value.get(1).asText()));
                }
            }
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable PromQL response", e);
        }
    }

    private HttpClient httpClient(HubCredentialsResolver.HubCredentials credentials) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getCollector().getConnectTimeoutMs()));
        if (credentials.caCertificate() != null) {
            builder.sslContext(trusting(credentials.caCertificate()));
        }
        return builder.build();
    }

    /** Trusts exactly the CA certificates in the hub Secret's {@code ca.crt}. */
    private static SSLContext trusting(Path caCertificate) {
        try (InputStream in = Files.newInputStream(caCertificate)) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                trustStore.setCertificateEntry("hub-ca-" + index++, certificate);
            }
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException e) {
            throw new AcmAccessException("Cannot load the hub CA certificate " + caCertificate, e);
        }
    }
}
