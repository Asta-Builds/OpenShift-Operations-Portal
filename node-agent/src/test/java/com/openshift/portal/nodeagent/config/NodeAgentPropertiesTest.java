package com.openshift.portal.nodeagent.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The agent refuses to start with these violations, before it sends anything. */
class NodeAgentPropertiesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void clusterNameAndPortalUrlAreRequired() {
        assertThat(violations(new NodeAgentProperties())).containsExactlyInAnyOrder("clusterName", "portal.url");
    }

    @Test
    void clusterNameMustBeAKubernetesName() {
        NodeAgentProperties properties = valid();
        properties.setClusterName("Prod East");

        assertThat(violations(properties)).containsExactly("clusterName");
    }

    @Test
    void aTokenEndpointNeedsClientCredentials() {
        NodeAgentProperties properties = valid();
        properties.getAuth().setTokenUri("https://keycloak.example.com/realms/openshift-portal/protocol/openid-connect/token");

        assertThat(violations(properties)).containsExactly("auth.credentialsComplete");

        properties.getAuth().setClientId("portal-node-agent");
        properties.getAuth().setClientSecret("s3cret");
        assertThat(violations(properties)).isEmpty();
    }

    private static NodeAgentProperties valid() {
        NodeAgentProperties properties = new NodeAgentProperties();
        properties.setClusterName("prod-east-1");
        properties.getPortal().setUrl("https://portal.example.com/api/v1");
        return properties;
    }

    private static Set<String> violations(NodeAgentProperties properties) {
        return VALIDATOR.validate(properties).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
