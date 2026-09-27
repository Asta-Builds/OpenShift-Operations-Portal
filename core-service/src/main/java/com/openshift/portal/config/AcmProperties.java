package com.openshift.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openshift.portal")
@Data
public class AcmProperties {
    private Collector collector = new Collector();
    private Simulator simulator = new Simulator();
    private Licensing licensing = new Licensing();
    private Security security = new Security();

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
    }

    @Data
    public static class Security {
        private boolean enabled = false;
    }
}
