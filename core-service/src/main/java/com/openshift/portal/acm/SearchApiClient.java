package com.openshift.portal.acm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads namespace labels from the ACM Search API (the {@code search-api} GraphQL service on the hub), which indexes
 * the resources of every managed cluster, so nothing runs on the managed clusters themselves. The endpoint is the
 * full GraphQL URL, typically a route to {@code https://search-search-api.open-cluster-management.svc:4010/searchapi/graphql}.
 */
@Component
@RequiredArgsConstructor
public class SearchApiClient {

    static final String NAMESPACE_QUERY = "query searchResult($input: [SearchInput]) { searchResult: search(input: $input) { items } }";

    private final HubHttpClient hubHttpClient;
    private final ObjectMapper objectMapper;

    /** Labels per cluster, then per namespace. A namespace without labels maps to an empty map. */
    public Map<String, Map<String, Map<String, String>>> namespaceLabels(String searchUrl,
                                                                       HubCredentialsResolver.HubCredentials credentials) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "operationName", "searchResult",
                    "query", NAMESPACE_QUERY,
                    "variables", Map.of("input", List.of(Map.of(
                            "keywords", List.of(),
                            "filters", List.of(Map.of("property", "kind", "values", List.of("Namespace"))),
                            "limit", -1)))));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build the Search API request", e);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return parse(hubHttpClient.send(request, credentials, "ACM Search at " + searchUrl));
    }

    Map<String, Map<String, Map<String, String>>> parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("errors").size() > 0) {
                throw new IllegalStateException("Search API query failed: " + root.path("errors").get(0).path("message").asText());
            }
            Map<String, Map<String, Map<String, String>>> labels = new HashMap<>();
            for (JsonNode result : root.path("data").path("searchResult")) {
                for (JsonNode item : result.path("items")) {
                    String cluster = item.path("cluster").asText(null);
                    String name = item.path("name").asText(null);
                    if (cluster != null && name != null) {
                        labels.computeIfAbsent(cluster, key -> new HashMap<>())
                                .put(name, parseLabels(item.path("label").asText("")));
                    }
                }
            }
            return labels;
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable Search API response", e);
        }
    }

    /** Search returns labels as one string, {@code "key=value; key2=value2"}. */
    static Map<String, String> parseLabels(String label) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String pair : label.split(";")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                labels.put(pair.substring(0, separator).trim(), pair.substring(separator + 1).trim());
            }
        }
        return labels;
    }
}
