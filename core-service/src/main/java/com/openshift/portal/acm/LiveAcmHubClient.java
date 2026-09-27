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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Reads managed clusters from a real ACM hub:
 * <ul>
 *   <li>inventory and capacity from {@code ManagedCluster} resources on the hub API server;</li>
 *   <li>per-namespace requests, usage and PVC requests from ACM Observability, when the hub has an endpoint for it.
 *       A cluster's requested CPU, memory and storage are the sums over its namespaces;</li>
 *   <li>namespace labels, and so ownership, from ACM Search, when the hub has an endpoint for it.</li>
 * </ul>
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
    private final SearchApiClient searchClient;
    private final AcmProperties properties;

    @Override
    public List<ClusterObservation> fetchClusters(AcmHub hub) {
        HubCredentialsResolver.HubCredentials credentials = credentialsResolver.resolve(hub);
        List<ManagedClusterMapper.ManagedClusterState> clusters = listManagedClusters(hub, credentials);
        Metrics metrics = readMetrics(hub, credentials);
        Map<String, Map<String, Map<String, String>>> labels = readLabels(hub, credentials);

        return clusters.stream().map(cluster -> toObservation(cluster, metrics, labels)).toList();
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

    private Metrics readMetrics(AcmHub hub, HubCredentialsResolver.HubCredentials credentials) {
        String url = hub.getObservabilityUrl();
        if (url == null || url.isBlank()) {
            log.warn("ACM Hub {} has no Observability endpoint; requests and usage are not collected", hub.getName());
            return null;
        }
        AcmProperties.Queries queries = properties.getAcm().getQueries();
        return new Metrics(
                metricsClient.queryByNamespace(url, credentials, queries.getNamespaceCpuRequests()),
                metricsClient.queryByNamespace(url, credentials, queries.getNamespaceMemoryRequests()),
                metricsClient.queryByNamespace(url, credentials, queries.getNamespaceCpuUsage()),
                metricsClient.queryByNamespace(url, credentials, queries.getNamespaceMemoryUsage()),
                metricsClient.queryByNamespace(url, credentials, queries.getNamespacePvcRequests()),
                metricsClient.queryByCluster(url, credentials, queries.getClusterPvCapacity()));
    }

    /**
     * Labels from ACM Search, or null when the hub has no Search endpoint or Search failed. Search only feeds
     * ownership, so its failure does not fail the hub: ownership recorded earlier is kept until it answers again.
     */
    private Map<String, Map<String, Map<String, String>>> readLabels(AcmHub hub,
                                                                     HubCredentialsResolver.HubCredentials credentials) {
        String url = hub.getSearchUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return searchClient.namespaceLabels(url, credentials);
        } catch (RuntimeException e) {
            log.warn("ACM Search of hub {} failed, namespace ownership is not refreshed this cycle: {}",
                    hub.getName(), e.getMessage());
            return null;
        }
    }

    private static ClusterObservation toObservation(ManagedClusterMapper.ManagedClusterState cluster, Metrics metrics,
                                                    Map<String, Map<String, Map<String, String>>> labels) {
        if (!cluster.available()) {
            return ClusterObservation.failed(cluster.name(),
                    "ManagedCluster " + cluster.name() + " is not available on its hub");
        }
        NamespaceInventory namespaces = namespaces(cluster.name(), metrics, labels);
        BigDecimal cpuRequests = BigDecimal.ZERO;
        BigDecimal memoryRequests = BigDecimal.ZERO;
        BigDecimal pvcRequests = BigDecimal.ZERO;
        if (namespaces != null) {
            for (NamespaceObservation namespace : namespaces.namespaces()) {
                cpuRequests = cpuRequests.add(namespace.cpuRequestCores());
                memoryRequests = memoryRequests.add(namespace.memoryRequestGb());
                pvcRequests = pvcRequests.add(namespace.pvcRequestGb() != null ? namespace.pvcRequestGb() : BigDecimal.ZERO);
            }
        }
        return new ClusterObservation(
                cluster.name(),
                cluster.cpuCores(),
                cpuRequests,
                cluster.memoryGb(),
                memoryRequests,
                metrics != null ? gigabytes(metrics.pvCapacity().get(cluster.name())) : BigDecimal.ZERO,
                pvcRequests,
                List.of(),
                namespaces,
                cluster.rawJson(),
                cluster.metadata(),
                null);
    }

    /**
     * The cluster's namespaces from whichever sources the hub has. Search lists every namespace, so its list is
     * complete; metrics only show namespaces with pods or claims. Null when neither source is configured.
     */
    private static NamespaceInventory namespaces(String clusterName, Metrics metrics,
                                                 Map<String, Map<String, Map<String, String>>> labels) {
        if (metrics == null && labels == null) {
            return null;
        }
        Map<String, Map<String, String>> clusterLabels = labels != null ? labels.get(clusterName) : null;
        TreeSet<String> names = new TreeSet<>();
        if (clusterLabels != null) {
            names.addAll(clusterLabels.keySet());
        }
        if (metrics != null) {
            names.addAll(metrics.cpuRequests().getOrDefault(clusterName, Map.of()).keySet());
            names.addAll(metrics.memoryRequests().getOrDefault(clusterName, Map.of()).keySet());
            names.addAll(metrics.pvcRequests().getOrDefault(clusterName, Map.of()).keySet());
        }

        List<NamespaceObservation> namespaces = new ArrayList<>();
        for (String name : names) {
            namespaces.add(new NamespaceObservation(
                    name,
                    clusterLabels != null ? clusterLabels.get(name) : null,
                    metrics != null ? cores(value(metrics.cpuRequests(), clusterName, name), BigDecimal.ZERO) : BigDecimal.ZERO,
                    metrics != null ? gigabytes(value(metrics.memoryRequests(), clusterName, name)) : BigDecimal.ZERO,
                    metrics != null ? cores(value(metrics.cpuUsage(), clusterName, name), null) : null,
                    metrics != null ? gigabytesOrNull(value(metrics.memoryUsage(), clusterName, name)) : null,
                    metrics != null ? gigabytesOrNull(value(metrics.pvcRequests(), clusterName, name)) : null));
        }
        // An empty Search result for a cluster usually means Search does not index it, not that it has no namespaces
        boolean complete = clusterLabels != null && !clusterLabels.isEmpty();
        return new NamespaceInventory(namespaces, complete);
    }

    private static BigDecimal value(Map<String, Map<String, BigDecimal>> byCluster, String cluster, String namespace) {
        return byCluster.getOrDefault(cluster, Map.of()).get(namespace);
    }

    private static BigDecimal cores(BigDecimal value, BigDecimal whenMissing) {
        return value == null ? whenMissing : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal gigabytes(BigDecimal bytes) {
        return bytes == null ? BigDecimal.ZERO : bytes.divide(BYTES_PER_GB, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal gigabytesOrNull(BigDecimal bytes) {
        return bytes == null ? null : gigabytes(bytes);
    }

    private record Metrics(Map<String, Map<String, BigDecimal>> cpuRequests,
                           Map<String, Map<String, BigDecimal>> memoryRequests,
                           Map<String, Map<String, BigDecimal>> cpuUsage,
                           Map<String, Map<String, BigDecimal>> memoryUsage,
                           Map<String, Map<String, BigDecimal>> pvcRequests,
                           Map<String, BigDecimal> pvCapacity) {
    }
}
