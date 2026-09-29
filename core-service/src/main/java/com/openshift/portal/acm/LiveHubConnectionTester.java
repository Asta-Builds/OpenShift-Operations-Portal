package com.openshift.portal.acm;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.dto.HubConnectionTestDto;
import com.openshift.portal.dto.HubConnectionTestDto.Check;
import com.openshift.portal.dto.HubConnectionTestDto.Status;
import com.openshift.portal.dto.HubConnectionTestDto.Target;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Tests a real hub with the same requests a collection sends. */
@Component
@ConditionalOnProperty(prefix = "openshift.portal.simulator", name = "enabled", havingValue = "false")
@RequiredArgsConstructor
public class LiveHubConnectionTester implements HubConnectionTester {

    private final HubCredentialsResolver credentialsResolver;
    private final ObservabilityMetricsClient metricsClient;
    private final SearchApiClient searchClient;
    private final AcmProperties properties;

    @Override
    public HubConnectionTestDto test(AcmHub hub) {
        List<Check> checks = new ArrayList<>();
        long start = System.nanoTime();
        HubCredentialsResolver.HubCredentials credentials;
        try {
            credentials = credentialsResolver.resolve(hub);
        } catch (RuntimeException e) {
            checks.add(new Check(Target.CREDENTIALS, Status.FAILED, e.getMessage(), elapsedMs(start)));
            for (Target target : List.of(Target.API, Target.OBSERVABILITY, Target.SEARCH)) {
                checks.add(new Check(target, Status.SKIPPED, "Not tried: needs the hub's token", 0));
            }
            return HubConnectionTestDto.of(checks);
        }
        checks.add(new Check(Target.CREDENTIALS, Status.OK, "Token read from Secret " + hub.getCredentialsSecretRef()
                + (credentials.caCertificate() != null ? "; trusting its ca.crt" : "; no ca.crt, trusting the JVM's CAs"),
                elapsedMs(start)));

        checks.add(check(Target.API, () -> {
            List<ManagedClusterMapper.ManagedClusterState> clusters =
                    LiveAcmHubClient.listManagedClusters(hub, credentials, properties);
            if (clusters.isEmpty()) {
                return new Outcome(Status.WARNING, "Connected, but the token sees no ManagedClusters");
            }
            long available = clusters.stream().filter(ManagedClusterMapper.ManagedClusterState::available).count();
            return new Outcome(Status.OK, clusters.size() + " ManagedClusters visible, " + available + " available");
        }));

        String observabilityUrl = hub.getObservabilityUrl();
        if (isBlank(observabilityUrl)) {
            checks.add(new Check(Target.OBSERVABILITY, Status.SKIPPED,
                    "Not configured: requests and usage are not collected", 0));
        } else {
            checks.add(check(Target.OBSERVABILITY, () -> {
                Map<String, Map<String, BigDecimal>> series = metricsClient.queryByNamespace(
                        observabilityUrl, credentials, properties.getAcm().getQueries().getNamespaceCpuRequests());
                return series.isEmpty()
                        ? new Outcome(Status.WARNING, "Answered, but returned no CPU request series: check the recording"
                                + " rules and that the token may read the managed clusters' namespaces")
                        : new Outcome(Status.OK, "CPU request series for " + series.size() + " clusters");
            }));
        }

        String searchUrl = hub.getSearchUrl();
        if (isBlank(searchUrl)) {
            checks.add(new Check(Target.SEARCH, Status.SKIPPED,
                    "Not configured: namespace ownership is not collected", 0));
        } else {
            checks.add(check(Target.SEARCH, () -> {
                Map<String, Map<String, Map<String, String>>> labels = searchClient.namespaceLabels(searchUrl, credentials);
                int namespaces = labels.values().stream().mapToInt(Map::size).sum();
                return namespaces == 0
                        ? new Outcome(Status.WARNING, "Answered, but returned no namespaces: check that the token may"
                                + " read the managed clusters' namespaces")
                        : new Outcome(Status.OK, namespaces + " namespaces in " + labels.size() + " clusters");
            }));
        }
        return HubConnectionTestDto.of(checks);
    }

    private static Check check(Target target, Supplier<Outcome> call) {
        long start = System.nanoTime();
        try {
            Outcome outcome = call.get();
            return new Check(target, outcome.status(), outcome.message(), elapsedMs(start));
        } catch (RuntimeException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new Check(target, Status.FAILED, message, elapsedMs(start));
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Outcome(Status status, String message) {
    }
}
