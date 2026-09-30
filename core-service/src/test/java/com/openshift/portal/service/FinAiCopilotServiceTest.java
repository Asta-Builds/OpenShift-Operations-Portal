package com.openshift.portal.service;

import com.openshift.portal.domain.enums.FinOpsEfficiencyRating;
import com.openshift.portal.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinAiCopilotServiceTest {

    @Mock
    private FinOpsService finOpsService;

    @Mock
    private WhatIfSimulatorService whatIfSimulatorService;

    @Mock
    private LicensingService licensingService;

    @InjectMocks
    private FinAiCopilotService finAiCopilotService;

    private FinOpsOverviewDto mockOverview;
    private List<FinOpsNamespaceRecommendationDto> mockRecommendations;
    private LicenseAuditDto mockAudit;

    @BeforeEach
    void setUp() {
        mockOverview = FinOpsOverviewDto.builder()
                .totalMonthlyAllocatedCost(BigDecimal.valueOf(50000))
                .totalMonthlyWastedCost(BigDecimal.valueOf(20000))
                .overallFleetEfficiencyPercent(BigDecimal.valueOf(60.0))
                .build();

        mockRecommendations = List.of(
                FinOpsNamespaceRecommendationDto.builder()
                        .namespaceName("spark-batch-analytics")
                        .clusterName("ocp-ai-training-prod")
                        .rating(FinOpsEfficiencyRating.SEVERE_WASTE)
                        .avgCpuRequestCores(BigDecimal.valueOf(64.0))
                        .avgCpuUsageCores(BigDecimal.valueOf(16.0))
                        .recommendedCpuRequestCores(BigDecimal.valueOf(20.0))
                        .recommendedMemoryRequestGb(BigDecimal.valueOf(40.0))
                        .monthlyPotentialSavings(BigDecimal.valueOf(2500.00))
                        .build()
        );

        mockAudit = LicenseAuditDto.builder()
                .licensedCapCores(1000)
                .totalLicenseCores(1250)
                .complianceBreach(true)
                .complianceStatus(LicenseAuditDto.ComplianceStatus.BREACH)
                .build();
    }

    @Test
    void testGetQuickPrompts() {
        List<FinAiQuickPromptDto> prompts = finAiCopilotService.getQuickPrompts();
        assertThat(prompts).isNotEmpty();
        assertThat(prompts).anyMatch(p -> p.getCategory().equals("CLI_REMEDIATION"));
    }

    @Test
    void testCliRemediationIntent() {
        when(finOpsService.getOverview(any(), any(), any())).thenReturn(mockOverview);
        when(finOpsService.getRecommendations(any(), any(), any(), any(), any(), any())).thenReturn(mockRecommendations);
        when(licensingService.generateLicenseAudit()).thenReturn(mockAudit);

        FinAiPromptRequestDto request = FinAiPromptRequestDto.builder()
                .prompt("Génère la commande oc patch pour spark-batch-analytics")
                .selectedNamespace("spark-batch-analytics")
                .build();

        FinAiResponseDto response = finAiCopilotService.ask(request);

        assertThat(response).isNotNull();
        assertThat(response.getHeadline()).contains("spark-batch-analytics");
        assertThat(response.getCliCommands()).isNotEmpty();
        assertThat(response.getCliCommands().get(0).getCommand()).contains("oc patch resourcequota");
        assertThat(response.getMetrics()).isNotEmpty();
    }

    @Test
    void testLicenseComplianceIntent() {
        when(finOpsService.getOverview(any(), any(), any())).thenReturn(mockOverview);
        when(finOpsService.getRecommendations(any(), any(), any(), any(), any(), any())).thenReturn(mockRecommendations);
        when(licensingService.generateLicenseAudit()).thenReturn(mockAudit);

        FinAiPromptRequestDto request = FinAiPromptRequestDto.builder()
                .prompt("Comment résoudre le dépassement de quota de licence (Cap Exceeded) ?")
                .build();

        FinAiResponseDto response = finAiCopilotService.ask(request);

        assertThat(response).isNotNull();
        assertThat(response.getHeadline()).contains("dépassement de licence");
        assertThat(response.getCliCommands()).isNotEmpty();
        assertThat(response.getCliCommands().get(0).getCommand()).contains("node-role.kubernetes.io/infra");
    }

    @Test
    void testActionPlanIntent() {
        when(finOpsService.getOverview(any(), any(), any())).thenReturn(mockOverview);
        when(finOpsService.getRecommendations(any(), any(), any(), any(), any(), any())).thenReturn(mockRecommendations);
        when(licensingService.generateLicenseAudit()).thenReturn(mockAudit);

        FinAiPromptRequestDto request = FinAiPromptRequestDto.builder()
                .prompt("Donne-moi un plan d'action d'urgence pour réduire les coûts")
                .build();

        FinAiResponseDto response = finAiCopilotService.ask(request);

        assertThat(response).isNotNull();
        assertThat(response.getHeadline()).contains("Plan d'action validé");
        assertThat(response.getExecutionPlan()).isNotEmpty();
    }
}
