package com.openshift.portal.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps Keycloak realm roles ({@code realm_access.roles}) to portal authorities. In the sandbox realm the user roles
 * come from LDAP groups of the same name, and node agents get theirs through their client's service account; any
 * other realm role is ignored.
 */
public class KeycloakRealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Map<String, String> PORTAL_ROLES = Map.of(
            "portal-admin", "ROLE_ADMIN",
            "portal-operator", "ROLE_OPERATOR",
            "portal-viewer", "ROLE_VIEWER",
            "portal-node-agent", "ROLE_NODE_AGENT");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> PORTAL_ROLES.get(String.valueOf(role)))
                .filter(Objects::nonNull)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
