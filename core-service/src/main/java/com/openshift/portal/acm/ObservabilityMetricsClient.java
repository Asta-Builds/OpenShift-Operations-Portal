package com.openshift.portal.acm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Instant PromQL queries against an ACM Observability endpoint (the Thanos querier behind the
 * {@code rbac-query-proxy} route). Series are keyed by the {@code cluster} label ACM adds to federated series.
 * Metrics missing from the hub's allowlist simply return no series.
 */
@Component
@RequiredArgsConstructor
public class ObservabilityMetricsClient {

    private final HubHttpClient hubHttpClient;
    private final ObjectMapper objectMapper;

    /** Value per cluster name for a query summed by {@code cluster}. */
    public Map<String, BigDecimal> queryByCluster(String observabilityUrl,
                                                  HubCredentialsResolver.HubCredentials credentials, String promQl) {
        Map<String, BigDecimal> values = new HashMap<>();
        forEachSample(run(observabilityUrl, credentials, promQl), (metric, value) -> {
            String cluster = metric.path("cluster").asText(null);
            if (cluster != null) {
                values.put(cluster, value);
            }
        });
        return values;
    }

    /** Value per cluster, then per namespace, for a query summed by {@code cluster} and {@code namespace}. */
    public Map<String, Map<String, BigDecimal>> queryByNamespace(String observabilityUrl,
                                                                 HubCredentialsResolver.HubCredentials credentials,
                                                                 String promQl) {
        Map<String, Map<String, BigDecimal>> values = new HashMap<>();
        forEachSample(run(observabilityUrl, credentials, promQl), (metric, value) -> {
            String cluster = metric.path("cluster").asText(null);
            String namespace = metric.path("namespace").asText(null);
            if (cluster != null && namespace != null) {
                values.computeIfAbsent(cluster, key -> new HashMap<>()).put(namespace, value);
            }
        });
        return values;
    }

    private String run(String observabilityUrl, HubCredentialsResolver.HubCredentials credentials, String promQl) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(observabilityUrl.replaceAll("/+$", "") + "/api/v1/query?query="
                        + URLEncoder.encode(promQl, StandardCharsets.UTF_8)))
                .GET();
        return hubHttpClient.send(request, credentials, "Observability at " + observabilityUrl);
    }

    private void forEachSample(String body, SampleConsumer consumer) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"success".equals(root.path("status").asText())) {
                throw new IllegalStateException("PromQL query failed: " + root.path("error").asText("unknown error"));
            }
            for (JsonNode series : root.path("data").path("result")) {
                JsonNode value = series.path("value");
                if (value.size() == 2 && isNumber(value.get(1).asText())) {
                    consumer.accept(series.path("metric"), new BigDecimal(value.get(1).asText()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable PromQL response", e);
        }
    }

    /** Prometheus writes samples as strings and may send NaN or ±Inf, which carry no usable value. */
    private static boolean isNumber(String sample) {
        try {
            new BigDecimal(sample);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface SampleConsumer {
        void accept(JsonNode metric, BigDecimal value);
    }
}
