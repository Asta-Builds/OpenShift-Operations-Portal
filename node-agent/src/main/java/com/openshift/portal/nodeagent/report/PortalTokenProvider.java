package com.openshift.portal.nodeagent.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openshift.portal.nodeagent.config.NodeAgentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

/**
 * Access tokens for the portal from Keycloak's client credentials grant. A token is reused until shortly before it
 * expires, so the token endpoint sees about one request per token lifetime.
 */
@Component
public class PortalTokenProvider {

    /** Tokens are renewed this long before they expire, so one never lapses on its way to the portal. */
    static final long EXPIRY_MARGIN_SECONDS = 30;

    private final RestClient restClient;
    private final NodeAgentProperties.Auth auth;
    private final Clock clock;

    private String token;
    private Instant renewAt = Instant.MIN;

    @Autowired
    public PortalTokenProvider(RestClient portalRestClient, NodeAgentProperties properties) {
        this(portalRestClient, properties, Clock.systemUTC());
    }

    PortalTokenProvider(RestClient restClient, NodeAgentProperties properties, Clock clock) {
        this.restClient = restClient;
        this.auth = properties.getAuth();
        this.clock = clock;
    }

    /** The bearer token to send, or null when no token endpoint is configured. */
    public synchronized String token() {
        if (!auth.isEnabled()) {
            return null;
        }
        if (token == null || !clock.instant().isBefore(renewAt)) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", auth.getClientId());
            form.add("client_secret", auth.getClientSecret());
            TokenResponse response = restClient.post()
                    .uri(auth.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("Token endpoint " + auth.getTokenUri() + " returned no access token");
            }
            token = response.accessToken();
            renewAt = clock.instant().plusSeconds(Math.max(0, response.expiresIn() - EXPIRY_MARGIN_SECONDS));
        }
        return token;
    }

    /** Drops the cached token, e.g. after the portal rejected it. */
    public synchronized void invalidate() {
        token = null;
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }
}
