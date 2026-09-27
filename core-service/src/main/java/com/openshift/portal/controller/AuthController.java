package com.openshift.portal.controller;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.dto.AuthConfigDto;
import com.openshift.portal.dto.CurrentUserDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "OIDC Keycloak authentication endpoints and user profile")
public class AuthController {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AcmProperties properties;
    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final RoleHierarchy roleHierarchy;

    @GetMapping("/config")
    public ResponseEntity<AuthConfigDto> getAuthConfig() {
        boolean enabled = properties.getSecurity().isEnabled();
        return ResponseEntity.ok(AuthConfigDto.builder()
                .enabled(enabled)
                .issuer(enabled ? resourceServerProperties.getJwt().getIssuerUri() : null)
                .clientId(enabled ? properties.getSecurity().getUiClientId() : null)
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDto> getCurrentUser(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            // Security is disabled, so every endpoint is open to every caller
            return ResponseEntity.ok(CurrentUserDto.builder()
                    .username("anonymous")
                    .roles(List.of("ADMIN", "OPERATOR", "VIEWER"))
                    .build());
        }

        Jwt jwt = token.getToken();
        List<String> roles = roleHierarchy.getReachableGrantedAuthorities(token.getAuthorities()).stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .toList();
        return ResponseEntity.ok(CurrentUserDto.builder()
                .username(jwt.getClaimAsString("preferred_username"))
                .name(jwt.getClaimAsString("name"))
                .roles(roles)
                .build());
    }
}
