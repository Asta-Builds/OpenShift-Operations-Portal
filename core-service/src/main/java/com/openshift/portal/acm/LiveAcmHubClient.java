package com.openshift.portal.acm;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.exception.AcmAccessException;
import com.openshift.portal.exception.AcmConnectionException;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Reads managed clusters from a real ACM hub: inventory and capacity from {@code ManagedCluster} resources on the
 * hub API server, and requested CPU, memory and storage from ACM Observability when the hub has an endpoint for it.
 * Node inventory is not read yet (plan decision D2), so license cores cannot be counted for live clusters.
 */
@Component
@ConditionalOnProperty(prefix = "openshift.portal.simulator", name = "enabled", havingValue = "false")
@RequiredArgsConstructor
@Slf4j
public class LiveAcmHubClient implements AcmHubClient {

    static final ResourceDefinitionContext MANAGED_CLUSTER = new ResourceDefinitionContext.Builder()
            .withGroup("cluster.open-cluster-management.io")
            .withVersion("v1")
            .withKind("ManagedCluster")
            .withPlural("managedclusters")
            .withNamespaced(false)
            .build();

    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1024L * 1024 * 1024);

    private final HubCredentialsResolver credentialsResolver;
    private final ObservabilityMetricsClient metricsClient;
    private final AcmProperties properties;

    @Override
    public List<ClusterObservation> fetchClusters(AcmHub hub) {
        HubCredentialsResolver.HubCredentials credentials = credentialsResolver.resolve(hub);
        List<ManagedClusterMapper.ManagedClusterState> clusters = listManagedClusters(hub, credentials);

        Metrics metrics = Metrics.EMPTY;
        if (hub.getObservabilityUrl() != null && !hub.getObservabilityUrl().isBlank()) {
            metrics = new Metrics(
                    metricsClient.query(hub.getObservabilityUrl(), credentials, ObservabilityMetricsClient.CPU_REQUESTS),
                    metricsClient.query(hub.getObservabilityUrl(), credentials, ObservabilityMetricsClient.MEMORY_REQUESTS),
                    metricsClient.query(hub.getObservabilityUrl(), credentials, ObservabilityMetricsClient.PV_CAPACITY),
                    metricsClient.query(hub.getObservabilityUrl(), credentials, ObservabilityMetricsClient.PVC_REQUESTS));
        } else {
            log.warn("ACM Hub {} has no Observability endpoint; allocation metrics are not collected", hub.getName());
        }

        Metrics collected = metrics;
        return clusters.stream().map(cluster -> toObservation(cluster, collected)).toList();
    }

    private List<ManagedClusterMapper.ManagedClusterState> listManagedClusters(
            AcmHub hub, HubCredentialsResolver.HubCredentials credentials) {
        Config config = new ConfigBuilder()
                .withMasterUrl(hub.getApiUrl())
                .withOauthToken(credentials.token())
                .withCaCertFile(credentials.caCertificate() != null ? credentials.caCertificate().toString() : null)
                .withConnectionTimeout(properties.getCollector().getConnectTimeoutMs())
                .withRequestTimeout(properties.getCollector().getReadTimeoutMs())
                // Retries are handled per hub by HubResilience
                .withRequestRetryBackoffLimit(0)
                .build();
        try (KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build()) {
            List<GenericKubernetesResource> items = client.genericKubernetesResources(MANAGED_CLUSTER).list().getItems();
            return items.stream().map(ManagedClusterMapper::map).toList();
        } catch (KubernetesClientException e) {
            if (e.getCode() == 401 || e.getCode() == 403) {
                throw new AcmAccessException("ACM Hub " + hub.getName() + " refused the token (HTTP " + e.getCode() + ")", e);
            }
            throw new AcmConnectionException("Cannot list ManagedClusters on ACM Hub " + hub.getName() + ": " + e.getMessage(), e);
        }
    }

    private static ClusterObservation toObservation(ManagedClusterMapper.ManagedClusterState cluster, Metrics metrics) {
        if (!cluster.available()) {
            return ClusterObservation.failed(cluster.name(),
                    "ManagedCluster " + cluster.name() + " is not available on its hub");
        }
        return new ClusterObservation(
                cluster.name(),
                cluster.cpuCores(),
                metrics.cpuRequests().getOrDefault(cluster.name(), BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP).intValue(),
                cluster.memoryGb(),
                gigabytes(metrics.memoryRequests().get(cluster.name())),
                gigabytes(metrics.pvCapacity().get(cluster.name())),
                gigabytes(metrics.pvcRequests().get(cluster.name())),
                List.of(),
                cluster.rawJson(),
                cluster.metadata(),
                null);
    }

    private static BigDecimal gigabytes(BigDecimal bytes) {
        return bytes == null ? BigDecimal.ZERO : bytes.divide(BYTES_PER_GB, 2, RoundingMode.HALF_UP);
    }

    private record Metrics(Map<String, BigDecimal> cpuRequests, Map<String, BigDecimal> memoryRequests,
                           Map<String, BigDecimal> pvCapacity, Map<String, BigDecimal> pvcRequests) {
        static final Metrics EMPTY = new Metrics(Map.of(), Map.of(), Map.of(), Map.of());
    }
}
