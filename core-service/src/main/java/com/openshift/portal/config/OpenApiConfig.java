package com.openshift.portal.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "OpenShift Operations Portal REST API",
                version = "1.0.0",
                description = "Enterprise multi-cluster OpenShift operational visibility, licensing watermark audit, capacity runway forecasting, and ACM telemetry engine.",
                contact = @Contact(
                        name = "OpenShift Operations Platform Team",
                        url = "https://github.com/Asta-Builds/OpenShift-Operations-Portal"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        ),
        servers = {
                @Server(url = "/api/v1", description = "Current Server Environment")
        },
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Keycloak JWT Bearer Token for authenticated endpoints (portal-admin, portal-operator, portal-viewer)."
)
public class OpenApiConfig {
}
