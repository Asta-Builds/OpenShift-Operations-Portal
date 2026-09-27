package com.openshift.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "openshift.portal")
@Data
public class AcmProperties {
    private Collector collector = new Collector();
    private Simulator simulator = new Simulator();
    private Licensing licensing = new Licensing();
    private Security security = new Security();
    private Acm acm = new Acm();
    private Attribution attribution = new Attribution();
    private Inventory inventory = new Inventory();
    private NodeAgent nodeAgent = new NodeAgent();

    /** Node reports pushed by the node agent in each managed cluster (plan decision D2). */
    @Data
    public static class NodeAgent {
        /**
         * Collections use a cluster's latest report only while it is younger than this; after that the cluster is
         * treated as having no node data rather than showing nodes that may no longer exist.
         */
        private Duration maxReportAge = Duration.ofHours(1);
    }

    /**
     * Scheduled inventory import from a mounted folder: each {@code <source>.csv} (for example {@code cmdb.csv})
     * replaces that source's rows. Disabled while {@code importDir} is empty.
     */
    @Data
    public static class Inventory {
        private String importDir = "";
        private String importCron = "0 0 * * * *";
    }

    /** Namespace label keys that name the owning team and the cost center (plan decision D4). */
    @Data
    public static class Attribution {
        private String ownerLabel = "openshift.io/owner-team";
        private String costCenterLabel = "cost-center";
    }

    @Data
    public static class Acm {
        /**
         * Where hub Secrets are mounted: each hub's {@code credentials_secret_ref} is a directory here holding
         * {@code token} and, for a private CA, {@code ca.crt}.
         */
        private String credentialsDir = "/var/run/secrets/acm-hubs";

        /** PromQL sent to ACM Observability; metric names vary between versions, so each query can be replaced. */
        private Queries queries = new Queries();
    }

    /**
     * Namespace queries must return one series per {@code cluster} and {@code namespace} label; the cluster query
     * one series per {@code cluster}. Requests use recording rules that only count running and pending pods.
     */
    @Data
    public static class Queries {
        private String namespaceCpuRequests =
                "sum by (cluster, namespace) (namespace_cpu:kube_pod_container_resource_requests:sum)";
        private String namespaceMemoryRequests =
                "sum by (cluster, namespace) (namespace_memory:kube_pod_container_resource_requests:sum)";
        private String namespaceCpuUsage =
                "sum by (cluster, namespace) (node_namespace_pod_container:container_cpu_usage_seconds_total:sum_irate)";
        private String namespaceMemoryUsage =
                "sum by (cluster, namespace) (container_memory_working_set_bytes{container!=\"\"})";
        private String namespacePvcRequests =
                "sum by (cluster, namespace) (kube_persistentvolumeclaim_resource_requests_storage_bytes)";
        private String clusterPvCapacity = "sum by (cluster) (kube_persistentvolume_capacity_bytes)";
    }

    @Data
    public static class Collector {
        private boolean enabled = true;
        private String cron = "0 */15 * * * *";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
    }

    @Data
    public static class Simulator {
        private boolean enabled = true;
        private int defaultClusters = 5;
    }

    @Data
    public static class Licensing {
        private double defaultCoreMultiplier = 1.0;
        private int bareMetalSocketFactor = 16;
        /** Contracted subscription cores; the high watermark is compared with it. */
        private int licensedCapCores = 500;
        /** The high watermark is the highest daily peak over this many days, typically the subscription term. */
        private int watermarkPeriodDays = 365;
    }

    @Data
    public static class Security {
        private boolean enabled = false;
        /** Keycloak client the browser UI signs in with (public client, authorization code + PKCE). */
        private String uiClientId = "portal-ui";
    }
}
