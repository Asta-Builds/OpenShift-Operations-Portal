package com.openshift.portal.nodeagent.report;

import com.openshift.portal.nodeagent.config.NodeAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Sends node reports to the portal's {@code POST /node-reports}. */
@Component
@RequiredArgsConstructor
public class PortalClient {

    private final RestClient portalRestClient;
    private final PortalTokenProvider tokenProvider;
    private final NodeAgentProperties properties;

    /** Returns the portal's answer; throws when the portal rejects the report or cannot be reached. */
    public ReportReceipt send(NodeReport report) {
        String token = tokenProvider.token();
        try {
            return portalRestClient.post()
                    .uri(stripTrailingSlash(properties.getPortal().getUrl()) + "/node-reports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (token != null) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                        }
                    })
                    .body(report)
                    .retrieve()
                    .body(ReportReceipt.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            // Revoked or rotated in Keycloak before it expired: fetch a fresh one for the next report
            tokenProvider.invalidate();
            throw e;
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** What the portal stored; {@code registered} is false while no ACM hub has reported this cluster yet. */
    public record ReportReceipt(String clusterName, int nodes, boolean registered) {
    }
}
