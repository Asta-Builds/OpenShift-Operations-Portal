package com.openshift.portal;

import com.openshift.portal.config.KeycloakRealmRolesConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role x endpoint matrix with security enabled. Tokens carry Keycloak realm roles and go through the same role
 * converter as in production, so role mapping, the role hierarchy and the URL rules are all exercised.
 */
@SpringBootTest(properties = {
        "openshift.portal.security.enabled=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example.com/realms/openshift-portal"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityAccessMatrixTest {

    private static final String REPORT_BODY = "{\"title\":\"Matrix check\",\"reportType\":\"FLEET_CAPACITY\"}";

    /** Replaces the Keycloak-backed decoder; jwt() below supplies already-decoded tokens. */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    enum Caller {
        ANONYMOUS(null),
        NO_PORTAL_ROLE("offline_access"),
        VIEWER("portal-viewer"),
        OPERATOR("portal-operator"),
        ADMIN("portal-admin");

        private final String realmRole;

        Caller(String realmRole) {
            this.realmRole = realmRole;
        }
    }

    static Stream<Arguments> accessMatrix() {
        return Stream.of(
                // Public
                arguments(GET, "/actuator/info", Caller.ANONYMOUS, 200),
                arguments(GET, "/auth/config", Caller.ANONYMOUS, 200),
                // Everything else needs a token
                arguments(GET, "/fleet/overview", Caller.ANONYMOUS, 401),
                arguments(POST, "/clusters/collect", Caller.ANONYMOUS, 401),
                arguments(GET, "/auth/me", Caller.ANONYMOUS, 401),
                // Signed in without a portal role: may only ask who they are
                arguments(GET, "/auth/me", Caller.NO_PORTAL_ROLE, 200),
                arguments(GET, "/fleet/overview", Caller.NO_PORTAL_ROLE, 403),
                // Viewers read dashboards
                arguments(GET, "/fleet/overview", Caller.VIEWER, 200),
                arguments(GET, "/clusters", Caller.VIEWER, 200),
                arguments(GET, "/hubs", Caller.VIEWER, 200),
                arguments(GET, "/licensing/audit", Caller.VIEWER, 200),
                arguments(GET, "/forecasting/projection", Caller.VIEWER, 200),
                arguments(GET, "/reports", Caller.VIEWER, 200),
                arguments(GET, "/reports/export", Caller.VIEWER, 403),
                arguments(GET, "/reports/saved", Caller.VIEWER, 403),
                arguments(POST, "/clusters/collect", Caller.VIEWER, 403),
                arguments(GET, "/simulator/status", Caller.VIEWER, 403),
                arguments(POST, "/reports", Caller.VIEWER, 403),
                // Operators also collect, export and keep their own saved reports
                arguments(GET, "/fleet/overview", Caller.OPERATOR, 200),
                arguments(POST, "/clusters/collect", Caller.OPERATOR, 200),
                arguments(GET, "/reports/export", Caller.OPERATOR, 200),
                arguments(GET, "/reports/export/pdf", Caller.OPERATOR, 200),
                arguments(GET, "/reports/saved", Caller.OPERATOR, 200),
                arguments(POST, "/reports/saved", Caller.OPERATOR, 200),
                arguments(POST, "/reports", Caller.OPERATOR, 403),
                arguments(GET, "/simulator/status", Caller.OPERATOR, 403),
                arguments(GET, "/actuator/metrics", Caller.OPERATOR, 403),
                // Admins can do everything
                arguments(GET, "/fleet/overview", Caller.ADMIN, 200),
                arguments(POST, "/clusters/collect", Caller.ADMIN, 200),
                arguments(GET, "/reports/export", Caller.ADMIN, 200),
                arguments(POST, "/reports", Caller.ADMIN, 200),
                arguments(GET, "/simulator/status", Caller.ADMIN, 200),
                arguments(POST, "/simulator/fault?fail=false", Caller.ADMIN, 200),
                arguments(GET, "/actuator/metrics", Caller.ADMIN, 200)
        );
    }

    @ParameterizedTest(name = "{2} {0} {1} -> {3}")
    @MethodSource("accessMatrix")
    void enforcesTheAccessMatrix(HttpMethod method, String path, Caller caller, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = request(method, path);
        if (method == POST) {
            request.contentType(MediaType.APPLICATION_JSON).content(REPORT_BODY);
        }
        if (caller != Caller.ANONYMOUS) {
            request.with(token("matrix-user", caller.realmRole));
        }

        mockMvc.perform(request).andExpect(status().is(expectedStatus));
    }

    @Test
    void currentUserIncludesRolesImpliedByTheHierarchy() throws Exception {
        mockMvc.perform(get("/auth/me").with(token("alice-id", "portal-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice-id"))
                .andExpect(jsonPath("$.roles").value(contains("ADMIN", "OPERATOR", "VIEWER")));
    }

    @Test
    void authConfigTellsTheUiWhereToSignIn() throws Exception {
        mockMvc.perform(get("/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.issuer").value("https://keycloak.example.com/realms/openshift-portal"))
                .andExpect(jsonPath("$.clientId").value("portal-ui"));
    }

    @Test
    void savedReportsBelongToTheTokenSubject() throws Exception {
        mockMvc.perform(post("/reports/saved").with(token("bob-id", "portal-operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bob's preset\",\"reportType\":\"LICENSE_AUDIT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("bob-id"));

        mockMvc.perform(get("/reports/saved").with(token("bob-id", "portal-operator")))
                .andExpect(jsonPath("$[*].title").value(hasItem("Bob's preset")));
        mockMvc.perform(get("/reports/saved").with(token("carl-id", "portal-operator")))
                .andExpect(jsonPath("$[*].title").value(not(hasItem("Bob's preset"))));
    }

    /** A decoded Keycloak access token for the given subject and realm role, mapped by the production converter. */
    private static RequestPostProcessor token(String subject, String realmRole) {
        return jwt()
                .jwt(jwt -> jwt.subject(subject)
                        .claim("preferred_username", subject)
                        .claim("realm_access", Map.of("roles", List.of(realmRole))))
                .authorities(new KeycloakRealmRolesConverter());
    }
}
