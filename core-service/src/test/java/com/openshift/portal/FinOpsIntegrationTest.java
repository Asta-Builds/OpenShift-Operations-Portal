package com.openshift.portal;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.Namespace;
import com.openshift.portal.domain.entity.NamespaceSnapshot;
import com.openshift.portal.domain.entity.Team;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.FinOpsEfficiencyRating;
import com.openshift.portal.domain.enums.FinOpsRecommendationAction;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.dto.FinOpsNamespaceRecommendationDto;
import com.openshift.portal.dto.FinOpsOverviewDto;
import com.openshift.portal.dto.FinOpsPricingConfigDto;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.NamespaceRepository;
import com.openshift.portal.repository.NamespaceSnapshotRepository;
import com.openshift.portal.repository.TeamRepository;
import com.openshift.portal.service.FinOpsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:finopsdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "openshift.portal.simulator.enabled=false"
})
@ActiveProfiles("dev")
@Transactional
class FinOpsIntegrationTest {

    @Autowired
    private FinOpsService finOpsService;

    @Autowired
    private AcmHubRepository hubRepository;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private NamespaceSnapshotRepository namespaceSnapshotRepository;

    private Cluster cluster;
    private Team team;
    private Namespace wastefulNs;
    private Namespace optimalNs;

    @BeforeEach
    void setUp() {
        AcmHub hub = hubRepository.save(AcmHub.builder()
                .name("finops-hub")
                .apiUrl("https://hub.example.com:6443")
                .status(HubStatus.ACTIVE)
                .build());

        cluster = clusterRepository.save(Cluster.builder()
                .clusterName("finops-cluster")
                .acmHub(hub)
                .environment(Environment.PRODUCTION)
                .infrastructureType(InfrastructureType.BARE_METAL)
                .build());

        team = teamRepository.save(Team.builder()
                .name("Core Banking")
                .costCenter("CC-BANK-100")
                .build());

        wastefulNs = namespaceRepository.save(Namespace.builder()
                .cluster(cluster)
                .namespaceName("payment-processing")
                .ownerTeam(team)
                .labelsCollected(true)
                .build());

        optimalNs = namespaceRepository.save(Namespace.builder()
                .cluster(cluster)
                .namespaceName("auth-service")
                .ownerTeam(team)
                .labelsCollected(true)
                .build());

        LocalDateTime now = LocalDateTime.now().minusDays(5);

        // Wasteful namespace: requests 16 cores and 64 GB, uses only 2 cores and 16 GB
        namespaceSnapshotRepository.save(NamespaceSnapshot.builder()
                .namespace(wastefulNs)
                .snapshotTimestamp(now)
                .cpuRequestCores(new BigDecimal("16.0"))
                .cpuUsageCores(new BigDecimal("2.0"))
                .memoryRequestGb(new BigDecimal("64.0"))
                .memoryUsageGb(new BigDecimal("16.0"))
                .pvcRequestGb(new BigDecimal("100.0"))
                .build());

        // Optimal namespace: requests 4 cores and 16 GB, uses 3.5 cores and 14 GB
        namespaceSnapshotRepository.save(NamespaceSnapshot.builder()
                .namespace(optimalNs)
                .snapshotTimestamp(now)
                .cpuRequestCores(new BigDecimal("4.0"))
                .cpuUsageCores(new BigDecimal("3.5"))
                .memoryRequestGb(new BigDecimal("16.0"))
                .memoryUsageGb(new BigDecimal("14.0"))
                .pvcRequestGb(new BigDecimal("50.0"))
                .build());
    }

    @Test
    void testFinOpsOverviewAndRightsizingRecommendations() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now();

        FinOpsOverviewDto overview = finOpsService.getOverview(from, to, null);

        assertThat(overview).isNotNull();
        assertThat(overview.getTotalMonthlyAllocatedCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(overview.getTotalMonthlyWastedCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(overview.getTotalAnnualizedSavingsPotential()).isGreaterThan(BigDecimal.ZERO);
        assertThat(overview.getTeamBreakdowns()).isNotEmpty();

        List<FinOpsNamespaceRecommendationDto> recommendations =
                finOpsService.getRecommendations(from, to, null, null, null, null);

        assertThat(recommendations).hasSize(2);

        FinOpsNamespaceRecommendationDto wastefulRec = recommendations.stream()
                .filter(r -> r.getNamespaceName().equals("payment-processing"))
                .findFirst().orElseThrow();

        assertThat(wastefulRec.getRating()).isIn(FinOpsEfficiencyRating.SEVERE_WASTE, FinOpsEfficiencyRating.OVER_PROVISIONED);
        assertThat(wastefulRec.getAction()).isEqualTo(FinOpsRecommendationAction.DOWNSIZE_CPU_AND_RAM);
        assertThat(wastefulRec.getMonthlyPotentialSavings()).isGreaterThan(BigDecimal.ZERO);
        assertThat(wastefulRec.getSuggestedResourceQuotaYaml()).contains("finops-optimized-quota");

        FinOpsNamespaceRecommendationDto optimalRec = recommendations.stream()
                .filter(r -> r.getNamespaceName().equals("auth-service"))
                .findFirst().orElseThrow();

        assertThat(optimalRec.getRating()).isIn(FinOpsEfficiencyRating.OPTIMAL, FinOpsEfficiencyRating.ACCEPTABLE);
    }

    @Test
    void testExportCsv() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now();

        byte[] csv = finOpsService.exportCsv(from, to, null);
        String csvContent = new String(csv);

        assertThat(csvContent).contains("Namespace,Cluster,Environment");
        assertThat(csvContent).contains("payment-processing");
        assertThat(csvContent).contains("auth-service");
        assertThat(csvContent).contains("Core Banking");
    }

    @Test
    void testPricingConfigUpdate() {
        FinOpsPricingConfigDto custom = FinOpsPricingConfigDto.builder()
                .cpuHourlyRate(new BigDecimal("0.060"))
                .memoryHourlyRate(new BigDecimal("0.008"))
                .storageMonthlyRate(new BigDecimal("0.15"))
                .currency("EUR")
                .build();

        FinOpsPricingConfigDto updated = finOpsService.updatePricingConfig(custom);
        assertThat(updated.getCurrency()).isEqualTo("EUR");
        assertThat(updated.getCpuHourlyRate()).isEqualByComparingTo("0.060");
    }
}
