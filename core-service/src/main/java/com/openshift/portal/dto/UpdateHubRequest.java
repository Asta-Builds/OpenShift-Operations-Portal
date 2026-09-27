package com.openshift.portal.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Changes a registered hub; fields left out keep their value. An empty string clears the optional Observability
 * and Search endpoints. The name cannot change, since clusters and history belong to the hub.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHubRequest {
    @Pattern(regexp = "https?://.+", message = "must be an http(s) URL")
    private String apiUrl;

    @Pattern(regexp = "[a-z0-9]([-a-z0-9.]*[a-z0-9])?", message = "must be a Kubernetes Secret name")
    private String credentialsSecretRef;

    @Pattern(regexp = "(https?://.+)?", message = "must be an http(s) URL, or empty to remove it")
    private String observabilityUrl;

    @Pattern(regexp = "(https?://.+)?", message = "must be an http(s) URL, or empty to remove it")
    private String searchUrl;
}
