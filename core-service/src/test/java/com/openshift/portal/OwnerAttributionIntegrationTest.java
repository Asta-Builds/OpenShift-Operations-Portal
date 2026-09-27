package com.openshift.portal;

import com.openshift.portal.acm.ClusterObservation;
import com.openshift.portal.acm.NamespaceInventory;
import com.openshift.portal.acm.NamespaceObservation;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.Namespace;
import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.dto.AttributionReportDto;
import com.openshift.portal.dto.AttributionReportDto.Row;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.NamespaceRepository;
import com.openshift.portal.repository.TeamRepository;
import com.openshift.portal.service.AttributionService;
import com.openshift.portal.service.SnapshotIngestionService;
import com.openshift.portal.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 acceptance: fixture namespaces, with and without owner labels, roll up to the right teams, and the
 * totals reconcile with cluster-level requests. Runs on its own database with the simulator off, and every test
 * writes into its own past period, so nothing else touches the data it reads.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attributiondb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "openshift.portal.simulator.enabled=false"
})
@ActiveProfiles("dev")
class OwnerAttributionIntegrationTest {

    private static final String OWNER = "openshift.io/owner-team";
    private static final String COST_CENTER = "cost-center";

    @Autowired
    private SnapshotIngestionService ingestionService;
    @Autowired
    private AttributionService attributionService;
    @Autowired
    private TeamService teamService;
    @Autowired
    private AcmHubRepository hubRepository;
    @Autowired
    private ClusterRepository clusterRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private NamespaceRepository namespaceRepository;

    private AcmHub hub;
    private Team payments;
    private Team web;

    @BeforeEach
    void teamsAndHub() {
        payments = teamRepository.findByName("Attribution Payments").orElseGet(() -> teamRepository.save(
                Team.builder().name("Attribution Payments").costCenter("CC-PAY").build()));
        web = teamRepository.findByName("Attribution Web").orElseGet(() -> teamRepository.save(
                Team.builder().name("Attribution Web").costCenter("CC-WEB").build()));
        if (teamService.listTeams().stream().noneMatch(t -> t.getAliases().contains("legacy-pay"))) {
            teamService.addAlias(payments.getId(), "Legacy Pay");
        }
        hub = hubRepository.findByName("attribution-hub").orElseGet(() -> hubRepository.save(AcmHub.builder()
                .name("attribution-hub").apiUrl("https://api.attribution.example.com:6443")
                .credentialsSecretRef("attribution-credentials").build()));
    }

    @Test
    void namespacesRollUpToTeamsAndReconcileWithClusterRequests() {
        // The cluster is owned by the web team, which must not absorb its unlabelled namespaces
        Cluster prod = cluster("attr-prod", Environment.PRODUCTION, web);
        Cluster dev = cluster("attr-dev", Environment.DEVELOPMENT, null);
        LocalDateTime at = LocalDateTime.of(2021, 3, 10, 12, 0);

        ingest(prod, at, true,
                ns("pay-api", labels("attribution-payments", "CC-X"), "10.00", "20.00"),
                ns("pay-batch", labels("LEGACY-PAY", null), "5.00", "10.00"),      // alias, any case
                ns("storefront", labels("Attribution-Web", null), "7.50", "12.00"),  // team name, any case
                ns("mystery", labels("nobody-team", null), "2.00", "4.00"),         // unknown team
                ns("kube-system", labels(null, null), "1.50", "2.00"));            // no owner label
        ingest(dev, at, false,
                ns("storefront-dev", labels("attribution-web", null), "4.00", "6.00"),
                ns("scratch", null, "2.00", "3.00"));                              // labels never read
        // A later collection of prod without namespace data (no Observability) must not dilute the averages
        ingestWithoutNamespaces(prod, at.plusHours(2), 90);

        AttributionReportDto report = attributionService.attribute(LocalDate.of(2021, 3, 10), LocalDate.of(2021, 3, 10), null);

        assertThat(row(report, "Attribution Payments", "CC-X").getCpuRequestCores()).isEqualByComparingTo("10.00");
        // Without a cost-center label the team's own cost center applies
        assertThat(row(report, "Attribution Payments", "CC-PAY").getCpuRequestCores()).isEqualByComparingTo("5.00");
        Row webRow = row(report, "Attribution Web", "CC-WEB");
        assertThat(webRow.getCpuRequestCores()).isEqualByComparingTo("11.50");
        assertThat(webRow.getClusterCount()).isEqualTo(2);
        assertThat(webRow.getNamespaceCount()).isEqualTo(2);

        Row unattributed = report.getUnattributed();
        assertThat(unattributed.getTeamName()).isEqualTo("Unattributed");
        assertThat(unattributed.getCpuRequestCores()).isEqualByComparingTo("5.50");
        assertThat(unattributed.getNamespaceCount()).isEqualTo(3);
        assertThat(report.getUnmappedOwners()).extracting(AttributionReportDto.UnmappedOwner::getOwnerLabelValue)
                .containsExactly("nobody-team");
        assertThat(report.getNamespacesWithoutOwnerLabel()).isEqualTo(1);
        assertThat(report.getNamespacesWithoutLabels()).isEqualTo(1);

        // Every namespace is in exactly one row, and the rows add up to the clusters' requests
        assertThat(report.getTotal().getCpuRequestCores()).isEqualByComparingTo("32.00");
        assertThat(report.getTotal().getCpuRequestCores()).isEqualByComparingTo(report.getClusterCpuRequestCores());
        assertThat(report.getTotal().getMemoryRequestGb()).isEqualByComparingTo(report.getClusterMemoryRequestGb());
        assertThat(report.getCpuCoveragePercent()).isEqualByComparingTo("100.0");
        assertThat(report.getCollections()).isEqualTo(3);
        assertThat(report.getCollectionsWithNamespaceData()).isEqualTo(2);

        AttributionReportDto prodOnly = attributionService.attribute(
                LocalDate.of(2021, 3, 10), LocalDate.of(2021, 3, 10), Environment.PRODUCTION);
        assertThat(prodOnly.getTotal().getCpuRequestCores()).isEqualByComparingTo("26.00");
        assertThat(prodOnly.getClusterCpuRequestCores()).isEqualByComparingTo("26.00");
    }

    @Test
    void namespacesThatComeAndGoCountForThePartOfThePeriodTheyExisted() {
        Cluster cluster = cluster("attr-churn", Environment.STAGING, null);
        LocalDateTime first = LocalDateTime.of(2021, 5, 1, 9, 0);
        LocalDateTime second = LocalDateTime.of(2021, 5, 2, 9, 0);

        ingest(cluster, first, true,
                ns("steady", labels("attribution-payments", null), "4.00", "8.00"),
                ns("temporary", labels("attribution-web", null), "2.00", "2.00"));
        ingest(cluster, second, true,
                ns("steady", labels("attribution-payments", null), "4.00", "8.00"));

        Namespace temporary = namespaceRepository.findByClusterIdAndNamespaceName(cluster.getId(), "temporary").orElseThrow();
        assertThat(temporary.getDeletedAt()).isEqualTo(second);

        AttributionReportDto report = attributionService.attribute(LocalDate.of(2021, 5, 1), LocalDate.of(2021, 5, 2), null);
        // Cluster requests were 6 then 4, so 5 on average; the temporary namespace only counts for one collection
        assertThat(row(report, "Attribution Payments", "CC-PAY").getCpuRequestCores()).isEqualByComparingTo("4.00");
        assertThat(row(report, "Attribution Web", "CC-WEB").getCpuRequestCores()).isEqualByComparingTo("1.00");
        assertThat(report.getClusterCpuRequestCores()).isEqualByComparingTo("5.00");
        assertThat(report.getTotal().getCpuRequestCores()).isEqualByComparingTo("5.00");

        // It comes back: no longer deleted
        ingest(cluster, LocalDateTime.of(2021, 5, 3, 9, 0), true,
                ns("temporary", labels("attribution-web", null), "1.00", "1.00"));
        assertThat(namespaceRepository.findByClusterIdAndNamespaceName(cluster.getId(), "temporary").orElseThrow()
                .getDeletedAt()).isNull();
    }

    @Test
    void ownershipIsKeptWhenLabelsCannotBeReadAndFollowsNewAliases() {
        Cluster cluster = cluster("attr-remap", Environment.PRODUCTION, null);
        ingest(cluster, LocalDateTime.of(2021, 7, 1, 9, 0), true,
                ns("owned", labels("attribution-payments", null), "3.00", "3.00"),
                ns("renamed-team", labels("pay-squad", null), "1.00", "1.00"));
        // Next cycle Search is down: labels unknown, list incomplete
        ingest(cluster, LocalDateTime.of(2021, 7, 2, 9, 0), false,
                ns("owned", null, "3.00", "3.00"));

        Namespace owned = namespaceRepository.findByClusterIdAndNamespaceName(cluster.getId(), "owned").orElseThrow();
        assertThat(owned.getOwnerTeam().getId()).isEqualTo(payments.getId());
        Namespace renamed = namespaceRepository.findByClusterIdAndNamespaceName(cluster.getId(), "renamed-team").orElseThrow();
        assertThat(renamed.getDeletedAt()).as("an incomplete list deletes nothing").isNull();
        assertThat(renamed.getOwnerTeam()).isNull();

        teamService.addAlias(payments.getId(), "pay-squad");

        renamed = namespaceRepository.findByClusterIdAndNamespaceName(cluster.getId(), "renamed-team").orElseThrow();
        assertThat(renamed.getOwnerTeam().getId()).isEqualTo(payments.getId());
        assertThat(renamed.getOwnerLabelValue()).isEqualTo("pay-squad");
    }

    private Cluster cluster(String name, Environment environment, Team owner) {
        return clusterRepository.findByClusterName(name).orElseGet(() -> clusterRepository.save(Cluster.builder()
                .acmHub(hub).clusterName(name).environment(environment).ownerTeam(owner)
                .infrastructureType(InfrastructureType.AWS).build()));
    }

    /** Stores one collection of the cluster; its requested CPU and memory are the namespaces' sums, as collected. */
    private void ingest(Cluster cluster, LocalDateTime at, boolean complete, NamespaceObservation... namespaces) {
        BigDecimal cpu = BigDecimal.ZERO;
        BigDecimal memory = BigDecimal.ZERO;
        for (NamespaceObservation ns : namespaces) {
            cpu = cpu.add(ns.cpuRequestCores());
            memory = memory.add(ns.memoryRequestGb());
        }
        ClusterObservation observation = new ClusterObservation(cluster.getClusterName(), 64, cpu,
                BigDecimal.valueOf(256), memory, BigDecimal.ZERO, BigDecimal.ZERO, List.of(),
                new NamespaceInventory(List.of(namespaces), complete), "{}", null, null);
        ingestionService.ingest(cluster, observation, at, false);
    }

    private void ingestWithoutNamespaces(Cluster cluster, LocalDateTime at, int cpuRequests) {
        ingestionService.ingest(cluster, new ClusterObservation(cluster.getClusterName(), 64, BigDecimal.valueOf(cpuRequests),
                BigDecimal.valueOf(256), BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO, List.of(),
                null, "{}", null, null), at, false);
    }

    private static NamespaceObservation ns(String name, Map<String, String> labels, String cpu, String memory) {
        return new NamespaceObservation(name, labels, new BigDecimal(cpu), new BigDecimal(memory), null, null, null);
    }

    private static Map<String, String> labels(String owner, String costCenter) {
        Map<String, String> labels = new HashMap<>();
        if (owner != null) {
            labels.put(OWNER, owner);
        }
        if (costCenter != null) {
            labels.put(COST_CENTER, costCenter);
        }
        return labels;
    }

    private static Row row(AttributionReportDto report, String team, String costCenter) {
        return report.getTeams().stream()
                .filter(r -> r.getTeamName().equals(team) && costCenter.equals(r.getCostCenter()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row for " + team + " / " + costCenter + " in " + report.getTeams()));
    }
}
