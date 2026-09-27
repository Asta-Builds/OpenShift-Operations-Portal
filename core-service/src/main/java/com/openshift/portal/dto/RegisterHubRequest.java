package com.openshift.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registers an ACM hub. The API token is never sent here: it lives in the Kubernetes Secret named by
 * {@code credentialsSecretRef}, mounted into the portal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterHubRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    /** Hub API server, e.g. https://api.hub.example.com:6443 */
    @NotBlank
    @Pattern(regexp = "https?://.+", message = "must be an http(s) URL")
    private String apiUrl;

    @NotBlank
    @Pattern(regexp = "[a-z0-9]([-a-z0-9.]*[a-z0-9])?", message = "must be a Kubernetes Secret name")
    private String credentialsSecretRef;

    /** Optional ACM Observability query endpoint (rbac-query-proxy route). */
    @Pattern(regexp = "https?://.+", message = "must be an http(s) URL")
    private String observabilityUrl;

    /** Optional ACM Search GraphQL endpoint; namespace ownership is read from it. */
    @Pattern(regexp = "https?://.+", message = "must be an http(s) URL")
    private String searchUrl;
}
