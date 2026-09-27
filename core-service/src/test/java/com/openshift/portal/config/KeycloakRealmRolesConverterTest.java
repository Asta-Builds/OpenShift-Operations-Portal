package com.openshift.portal.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRolesConverterTest {

    private final KeycloakRealmRolesConverter converter = new KeycloakRealmRolesConverter();

    @Test
    void mapsPortalRealmRolesAndIgnoresOthers() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles",
                List.of("portal-admin", "portal-viewer", "offline_access", "default-roles-openshift-portal"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_VIEWER");
    }

    @Test
    void nodeAgentServiceAccountGetsOnlyTheAgentRole() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles",
                List.of("portal-node-agent", "default-roles-openshift-portal"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_NODE_AGENT");
    }

    @Test
    void tokenWithoutRealmRolesGrantsNothing() {
        assertThat(converter.convert(token(Map.of("scope", "openid")))).isEmpty();
        assertThat(converter.convert(token(Map.of("realm_access", Map.of("roles", "portal-admin"))))).isEmpty();
    }

    private static Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject("user");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
