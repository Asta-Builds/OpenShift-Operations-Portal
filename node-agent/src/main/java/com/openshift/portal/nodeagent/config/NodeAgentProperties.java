package com.openshift.portal.nodeagent.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "node-agent")
@Validated
@Data
public class NodeAgentProperties {

    /** Name of this cluster as its ACM hub knows it (the ManagedCluster name); the portal matches reports on it. */
    @NotBlank
    @Pattern(regexp = "[a-z0-9]([-a-z0-9.]*[a-z0-9])?", message = "must be a Kubernetes resource name")
    private String clusterName;

    /** Reported with every report so the portal can show which agent version runs where. */
    private String version = "unknown";

    /** Time between two reports; the portal ignores reports older than its max report age (1 hour by default). */
    private Duration reportInterval = Duration.ofMinutes(5);

    /** Wait before the first report after start-up. */
    private Duration initialDelay = Duration.ofSeconds(10);

    @Valid
    private Portal portal = new Portal();

    @Valid
    private Auth auth = new Auth();

    @Data
    public static class Portal {
        /** Portal API base URL, including the context path, e.g. https://portal.apps.example.com/api/v1 */
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "must be an http(s) URL")
        private String url;

        /**
         * Spring SSL bundle (spring.ssl.bundle.*) that trusts the portal's and Keycloak's CA; empty uses the JVM's
         * default trust store.
         */
        private String sslBundle = "";

        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);
    }

    /**
     * Keycloak client credentials of this agent. With an empty token URI the agent sends no token, which only a
     * portal with security disabled accepts.
     */
    @Data
    public static class Auth {
        /** e.g. https://keycloak.example.com/realms/openshift-portal/protocol/openid-connect/token */
        @Pattern(regexp = "(https?://.+)?", message = "must be empty or an http(s) URL")
        private String tokenUri = "";
        private String clientId = "";
        private String clientSecret = "";

        public boolean isEnabled() {
            return tokenUri != null && !tokenUri.isBlank();
        }

        @AssertTrue(message = "client-id and client-secret are required when token-uri is set")
        public boolean isCredentialsComplete() {
            return !isEnabled() || (clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank());
        }
    }
}
