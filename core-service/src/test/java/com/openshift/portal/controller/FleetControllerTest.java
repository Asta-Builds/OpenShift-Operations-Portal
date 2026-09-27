package com.openshift.portal.controller;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.config.SecurityConfig;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.enums.HubStatus;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.ClusterSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FleetController.class)
@Import({SecurityConfig.class, AcmProperties.class})
class FleetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClusterRepository clusterRepository;

    @MockBean
    private AcmHubRepository acmHubRepository;

    @MockBean
    private ClusterSnapshotRepository snapshotRepository;

    @Test
    void getFleetOverview_returnsAggregatedMetrics() throws Exception {
        when(clusterRepository.count()).thenReturn(3L);
        when(acmHubRepository.countByStatus(HubStatus.ACTIVE)).thenReturn(1L);

        ClusterSnapshot snapshot = ClusterSnapshot.builder()
                .totalCpuCores(128)
                .allocatedCpuCores(BigDecimal.valueOf(64))
                .totalMemoryGb(BigDecimal.valueOf(512.0))
                .allocatedMemoryGb(BigDecimal.valueOf(256.0))
                .totalStorageGb(BigDecimal.valueOf(2000.0))
                .allocatedStorageGb(BigDecimal.valueOf(1000.0))
                .licenseCoresCount(48)
                .build();

        when(snapshotRepository.findLatestSnapshotsForAllClusters()).thenReturn(List.of(snapshot));
        when(clusterRepository.countByEnvironmentGroup()).thenReturn(List.<Object[]>of(new Object[]{"PRODUCTION", 3L}));
        when(clusterRepository.countByInfrastructureTypeGroup()).thenReturn(List.<Object[]>of(new Object[]{"BARE_METAL", 3L}));

        mockMvc.perform(get("/fleet/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClusters").value(3))
                .andExpect(jsonPath("$.activeAcmHubs").value(1))
                .andExpect(jsonPath("$.totalCpuCores").value(128))
                .andExpect(jsonPath("$.allocatedCpuCores").value(64))
                .andExpect(jsonPath("$.cpuUtilizationPercent").value(50.0))
                .andExpect(jsonPath("$.totalStorageGb").value(2000.0))
                .andExpect(jsonPath("$.allocatedStorageGb").value(1000.0))
                .andExpect(jsonPath("$.storageUtilizationPercent").value(50.0))
                .andExpect(jsonPath("$.totalLicenseCores").value(48));
    }
}
