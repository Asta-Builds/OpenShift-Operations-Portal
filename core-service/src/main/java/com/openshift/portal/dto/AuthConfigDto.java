package com.openshift.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tells the browser UI whether to sign in, and where. Served without authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthConfigDto {
    private boolean enabled;
    /** OpenID Connect issuer as browsers reach it; null when security is disabled. */
    private String issuer;
    private String clientId;
}
