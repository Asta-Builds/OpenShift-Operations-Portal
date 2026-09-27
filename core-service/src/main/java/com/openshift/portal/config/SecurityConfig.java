package com.openshift.portal.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

// No CORS configuration: the UI reaches the API same-origin (nginx in containers, the Angular proxy in development).
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AcmProperties properties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        if (!properties.getSecurity().isEnabled()) {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .build();
        }

        // Explicit MVC matchers stay unambiguous when other servlets (such as the H2 console) are registered
        MvcRequestMatcher.Builder mvc = new MvcRequestMatcher.Builder(introspector);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Paths are relative to the servlet context path (/api/v1); the first matching rule wins
                        .requestMatchers(mvc.pattern("/actuator/health/**"), mvc.pattern("/actuator/info"),
                                mvc.pattern("/auth/config"), mvc.pattern("/error"),
                                mvc.pattern("/v3/api-docs/**"), mvc.pattern("/swagger-ui/**"),
                                mvc.pattern("/swagger-ui.html")).permitAll()
                        .requestMatchers(mvc.pattern("/auth/me")).authenticated()
                        .requestMatchers(mvc.pattern("/actuator/**"), mvc.pattern("/simulator/**")).hasRole("ADMIN")
                        .requestMatchers(mvc.pattern(HttpMethod.POST, "/reports")).hasRole("ADMIN")
                        .requestMatchers(mvc.pattern(HttpMethod.POST, "/clusters/collect"),
                                mvc.pattern("/reports/export/**"), mvc.pattern("/reports/saved/**")).hasRole("OPERATOR")
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/**")).hasRole("VIEWER")
                        .anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /** An admin can do everything an operator can, and an operator everything a viewer can. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_OPERATOR
                ROLE_OPERATOR > ROLE_VIEWER
                """);
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }
}
