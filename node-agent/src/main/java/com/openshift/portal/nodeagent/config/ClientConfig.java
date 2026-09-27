package com.openshift.portal.nodeagent.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

    /**
     * Inside a cluster the client uses the pod's service account; outside one it follows KUBECONFIG or
     * ~/.kube/config, which is handy against a hub-lab cluster.
     */
    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }

    /** Client for the portal API and the Keycloak token endpoint; both share the configured trust. */
    @Bean
    public RestClient portalRestClient(RestClient.Builder builder, NodeAgentProperties properties, SslBundles sslBundles) {
        NodeAgentProperties.Portal portal = properties.getPortal();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(portal.getConnectTimeout())
                .withReadTimeout(portal.getReadTimeout());
        if (portal.getSslBundle() != null && !portal.getSslBundle().isBlank()) {
            settings = settings.withSslBundle(sslBundles.getBundle(portal.getSslBundle()));
        }
        return builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }
}
