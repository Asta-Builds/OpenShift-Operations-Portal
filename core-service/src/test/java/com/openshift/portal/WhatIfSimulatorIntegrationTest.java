package com.openshift.portal;

import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.FinOpsEfficiencyRating;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.dto.*;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import com.openshift.portal.service.WhatIfSimulatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:whatifdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "openshift.portal.simulator.enabled=false"
})
@ActiveProfiles("dev")
@Transactional
class WhatIfSimulatorIntegrationTest {

    @Autowired
    private WhatIfSimulatorService whatIfSimulatorService;

    @Autowired
    private AcmHubRepository hubRepository;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private ClusterSnapshotRepository snapshotRepository;

    private Cluster prodCluster;
    private Cluster devCluster;

    @BeforeEach
    void setUp() {
        AcmHub hub = hubRepository.save(AcmHub.builder()
                .name("whatif-hub")
                .apiUrl("https://hub-whatif.internal:6443")
                .status(HubStatus.ACTIVE)
                .build());

        prodCluster = clusterRepository.save(Cluster.builder()
                .clusterName("ocp-prod-eu-west-01")
                .acmHub(hub)
                .environment(Environment.PRODUCTION)
                .infrastructureType(InfrastructureType.BARE_METAL)
                .build());

        devCluster = clusterRepository.save(Cluster.builder()
                .clusterName("ocp-dev-us-east-sandbox")
                .acmHub(hub)
                .environment(Environment.DEVELOPMENT)
                .infrastructureType(InfrastructureType.VMWARE)
                .build());

        snapshotRepository.save(ClusterSnapshot.builder()
                .cluster(prodCluster)
                .snapshotTimestamp(LocalDateTime.now())
                .totalCpuCores(128)
                .allocatedCpuCores(new BigDecimal("96.00"))
                .totalMemoryGb(new BigDecimal("512.00"))
                .allocatedMemoryGb(new BigDecimal("384.00"))
                .totalStorageGb(new BigDecimal("5000.00"))
                .allocatedStorageGb(new BigDecimal("3200.00"))
                .licenseCoresCount(104)
                .build());

        snapshotRepository.save(ClusterSnapshot.builder()
                .cluster(devCluster)
                .snapshotTimestamp(LocalDateTime.now())
                .totalCpuCores(48)
                .allocatedCpuCores(new BigDecimal("28.00"))
                .totalMemoryGb(new BigDecimal("192.00"))
                .allocatedMemoryGb(new BigDecimal("110.00"))
                .totalStorageGb(new BigDecimal("2000.00"))
                .allocatedStorageGb(new BigDecimal("800.00"))
                .licenseCoresCount(32)
                .build());
    }

    @Test
    void testPresetsReturned() {
        List<WhatIfPresetDto> presets = whatIfSimulatorService.getPresets();
        assertThat(presets).isNotEmpty();
        assertThat(presets).extracting(WhatIfPresetDto::getId)
                .contains("preset-full-severe-waste", "preset-balanced-enterprise", "preset-onboard-genai-stack");
    }

    @Test
    void testSimulateRightsizingAdoption() {
        WhatIfSimulationRequestDto request = WhatIfSimulationRequestDto.builder()
                .rightsizingAdoptionPercent(100)
                .targetEfficiencyRatings(List.of(FinOpsEfficiencyRating.SEVERE_WASTE))
                .build();

        WhatIfSimulationResultDto result = whatIfSimulatorService.simulate(request);

        assertThat(result).isNotNull();
        assertThat(result.getClusterImpacts()).hasSize(2);
        assertThat(result.getSimulatedMonthlySpend()).isNotNull();
    }

    @Test
    void testSimulateWorkloadOnboarding() {
        WhatIfSimulationRequestDto request = WhatIfSimulationRequestDto.builder()
                .additionalWorkloads(List.of(
                        WhatIfWorkloadDto.builder()
                                .name("fraud-inference-ai")
                                .targetClusterId(prodCluster.getId())
                                .requestedCpuCores(32.0)
                                .requestedMemoryGb(128.0)
                                .requestedStorageGb(500.0)
                                .build()
                ))
                .build();

        WhatIfSimulationResultDto result = whatIfSimulatorService.simulate(request);

        assertThat(result).isNotNull();
        assertThat(result.getNewWorkloadsMonthlyCost()).isPositive();

        WhatIfClusterImpactDto prodImpact = result.getClusterImpacts().stream()
                .filter(c -> c.getClusterId().equals(prodCluster.getId()))
                .findFirst().orElseThrow();

        assertThat(prodImpact.getSimulatedAllocatedCores()).isEqualTo(128.0); // 96 + 32 = 128
        assertThat(prodImpact.getSimulatedCpuAllocPercent()).isEqualTo(100.0);
        assertThat(prodImpact.getHeadroomStatus().name()).contains("CRITICAL");
        assertThat(prodImpact.getWarnings()).isNotEmpty();
    }

    @Test
    void testSimulateClusterDecommission() {
        WhatIfSimulationRequestDto request = WhatIfSimulationRequestDto.builder()
                .clusterDecommissions(List.of(
                        WhatIfDecommissionDto.builder()
                                .sourceClusterId(devCluster.getId())
                                .targetClusterId(prodCluster.getId())
                                .build()
                ))
                .build();

        WhatIfSimulationResultDto result = whatIfSimulatorService.simulate(request);

        assertThat(result).isNotNull();
        assertThat(result.getHardwareAndLicenseMonthlySavings()).isPositive();
        assertThat(result.getGlobalWarnings()).isNotEmpty();

        WhatIfClusterImpactDto devImpact = result.getClusterImpacts().stream()
                .filter(c -> c.getClusterId().equals(devCluster.getId()))
                .findFirst().orElseThrow();

        assertThat(devImpact.isDecommissioned()).isTrue();
        assertThat(devImpact.getSimulatedAllocatedCores()).isEqualTo(0.0);

        WhatIfClusterImpactDto prodImpact = result.getClusterImpacts().stream()
                .filter(c -> c.getClusterId().equals(prodCluster.getId()))
                .findFirst().orElseThrow();

        // 96 baseline + 28 from dev = 124
        assertThat(prodImpact.getSimulatedAllocatedCores()).isEqualTo(124.0);
    }
}
