package com.openshift.portal;

import com.openshift.portal.acm.ClusterObservation;
import com.openshift.portal.acm.NodeObservation;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.ClusterSnapshot;
import com.openshift.portal.domain.entity.NodeMetricsSnapshot;
import com.openshift.portal.domain.enums.Environment;
import com.openshift.portal.domain.enums.InfrastructureType;
import com.openshift.portal.domain.enums.NodeRole;
import com.openshift.portal.domain.enums.ProviderType;
import com.openshift.portal.dto.InfrastructureTopologyDto;
import com.openshift.portal.exception.InventoryImportException;
import com.openshift.portal.repository.AcmHubRepository;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.InfrastructureInventoryRepository;
import com.openshift.portal.repository.NodeMetricsSnapshotRepository;
import com.openshift.portal.service.InfrastructureTopologyService;
import com.openshift.portal.service.InventoryImportService;
import com.openshift.portal.service.NodeCorrelationService.Status;
import com.openshift.portal.service.SnapshotIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 acceptance: hypervisor and hardware facts reach nodes only from inventory rows, matched on the instance
 * key in their providerID; nodes without a row are reported as such. Runs on its own database with the simulator off.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:infradb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "openshift.portal.simulator.enabled=false"
})
@ActiveProfiles("dev")
class InfrastructureCorrelationIntegrationTest {

    private static final String VM_IN_CMDB = "vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c";
    private static final String VM_NOT_IN_CMDB = "vsphere://4237aaaa-2a4b-d3c9-1b6e-6e1f2d3a4b5c";
    private static final String BARE_METAL = "baremetalhost:///openshift-machine-api/rack3-host7/5d1a8b3c-7e2f-4a6b-9c0d-1e2f3a4b5c6d";
    private static final String AWS = "aws:///eu-west-1a/i-0123456789abcdef0";

    @Autowired
    private SnapshotIngestionService ingestionService;
    @Autowired
    private InventoryImportService importService;
    @Autowired
    private InfrastructureTopologyService topologyService;
    @Autowired
    private InfrastructureInventoryRepository inventoryRepository;
    @Autowired
    private NodeMetricsSnapshotRepository nodeRepository;
    @Autowired
    private AcmHubRepository hubRepository;
    @Autowired
    private ClusterRepository clusterRepository;

    private ClusterSnapshot snapshot;

    @BeforeEach
    void collectOneCluster() {
        inventoryRepository.deleteAll();
        AcmHub hub = hubRepository.findByName("infra-hub").orElseGet(() -> hubRepository.save(AcmHub.builder()
                .name("infra-hub").apiUrl("https://api.infra.example.com:6443").credentialsSecretRef("infra").build()));
        Cluster cluster = clusterRepository.findByClusterName("infra-prod").orElseGet(() -> clusterRepository.save(
                Cluster.builder().acmHub(hub).clusterName("infra-prod").environment(Environment.PRODUCTION)
                        .infrastructureType(InfrastructureType.VMWARE).build()));
        List<NodeObservation> nodes = List.of(
                new NodeObservation("vm-known", NodeRole.WORKER, 16, BigDecimal.valueOf(64), VM_IN_CMDB),
                new NodeObservation("vm-unknown", NodeRole.WORKER, 16, BigDecimal.valueOf(64), VM_NOT_IN_CMDB),
                new NodeObservation("metal", NodeRole.WORKER, 64, BigDecimal.valueOf(512), BARE_METAL),
                new NodeObservation("cloud", NodeRole.WORKER, 8, BigDecimal.valueOf(32), AWS),
                new NodeObservation("upi", NodeRole.WORKER, 8, BigDecimal.valueOf(32), ""));
        snapshot = ingestionService.ingest(cluster, new ClusterObservation("infra-prod", 112, BigDecimal.TEN,
                BigDecimal.valueOf(704), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, nodes, null, "{}", null, null),
                LocalDateTime.now(), true);
    }

    @Test
    void withoutInventoryNoHypervisorIsInvented() {
        Map<String, NodeMetricsSnapshot> nodes = nodes();

        assertThat(nodes.values()).allSatisfy(node -> {
            assertThat(node.getHypervisorHost()).isNull();
            assertThat(node.getSockets()).isNull();
        });
        assertThat(nodes.get("vm-known").getProviderType()).isEqualTo(ProviderType.VSPHERE);
        assertThat(nodes.get("vm-known").getUnderlyingHostId()).isEqualTo("4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c");
        assertThat(nodes.get("cloud").getProviderZone()).isEqualTo("eu-west-1a");
        assertThat(topologyService.topology().summary())
                .isEqualTo(new InfrastructureTopologyDto.Summary(5, 0, 1, 3, 1));
    }

    @Test
    void importedRowsCorrelateTheLatestSnapshotRightAway() {
        // One row by providerID, one by type and key typed in upper case, one for a machine no node runs on
        String csv = """
                provider_id,provider_type,instance_key,hypervisor_host,hypervisor_cluster,datacenter,physical_sockets,physical_cores,threads_per_core
                %s,,,esx-07.fra.corp,vsan-prod,dc-frankfurt,2,48,2
                ,BAREMETAL,openshift-machine-api/rack3-host7,,,dc-frankfurt,2,32,2
                ,VSPHERE,4237FFFF-2A4B-D3C9-1B6E-6E1F2D3A4B5C,esx-01.fra.corp,vsan-prod,dc-frankfurt,2,48,2
                """.formatted(VM_IN_CMDB);

        var result = importService.importCsv(new StringReader(csv), "CMDB", false);

        assertThat(result.inserted()).isEqualTo(3);
        assertThat(result.matchedNodes()).isEqualTo(2);
        Map<String, NodeMetricsSnapshot> nodes = nodes();
        NodeMetricsSnapshot vm = nodes.get("vm-known");
        assertThat(vm.getHypervisorHost()).isEqualTo("esx-07.fra.corp");
        assertThat(vm.getHypervisorCluster()).isEqualTo("vsan-prod");
        assertThat(vm.getInventorySource()).isEqualTo("CMDB");
        assertThat(vm.getSockets()).isEqualTo(2);
        NodeMetricsSnapshot metal = nodes.get("metal");
        assertThat(metal.getHypervisorHost()).as("bare metal has no hypervisor").isNull();
        assertThat(metal.getPhysicalCores()).isEqualTo(32);
        assertThat(nodes.get("vm-unknown").getHypervisorHost()).isNull();

        InfrastructureTopologyDto topology = topologyService.topology();
        assertThat(topology.summary()).isEqualTo(new InfrastructureTopologyDto.Summary(5, 2, 1, 1, 1));
        assertThat(topology.hypervisorClusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.hypervisorCluster()).isEqualTo("vsan-prod");
            assertThat(cluster.hosts()).singleElement().satisfies(host -> {
                assertThat(host.hypervisorHost()).isEqualTo("esx-07.fra.corp");
                assertThat(host.nodes()).extracting(InfrastructureTopologyDto.Node::nodeName).containsExactly("vm-known");
            });
        });
        assertThat(topology.bareMetal()).singleElement()
                .satisfies(dc -> assertThat(dc.nodes()).extracting(InfrastructureTopologyDto.Node::nodeName).containsExactly("metal"));
        assertThat(topology.notInInventory()).extracting(InfrastructureTopologyDto.Node::nodeName).containsExactly("vm-unknown");
        assertThat(topology.notInInventory().get(0).status()).isEqualTo(Status.NOT_IN_INVENTORY);
        assertThat(topology.noProviderId()).extracting(InfrastructureTopologyDto.Node::nodeName).containsExactly("upi");
        assertThat(topology.cloud()).singleElement().satisfies(zone -> assertThat(zone.zone()).isEqualTo("eu-west-1a"));
    }

    @Test
    void aFullExportRemovesMachinesItNoLongerLists() {
        importService.importCsv(new StringReader("provider_id,hypervisor_host\n" + VM_IN_CMDB + ",esx-07\n"), "CMDB", false);
        assertThat(nodes().get("vm-known").getHypervisorHost()).isEqualTo("esx-07");

        var result = importService.importCsv(new StringReader("provider_id,hypervisor_host\n" + BARE_METAL + ",\n"), "CMDB", true);

        assertThat(result.removed()).isEqualTo(1);
        assertThat(nodes().get("vm-known").getHypervisorHost()).isNull();
        assertThat(nodes().get("vm-known").getInventorySource()).isNull();
    }

    @Test
    void theMostAuthoritativeSourceWins() {
        importService.importCsv(new StringReader("provider_id,hypervisor_host\n" + VM_IN_CMDB + ",esx-from-cmdb\n"), "CMDB", false);
        importService.importCsv(new StringReader("provider_id,hypervisor_host\n" + VM_IN_CMDB + ",esx-from-vcenter\n"), "VCENTER", false);

        assertThat(nodes().get("vm-known").getHypervisorHost()).isEqualTo("esx-from-vcenter");
        assertThat(nodes().get("vm-known").getInventorySource()).isEqualTo("VCENTER");
    }

    @Test
    void aFileWithErrorsChangesNothing() {
        String csv = """
                provider_type,instance_key,physical_sockets
                VSPHERE,4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c,2
                TOASTER,abc,1
                VSPHERE,4237C5F4-2A4B-D3C9-1B6E-6E1F2D3A4B5C,two
                """;

        assertThatThrownBy(() -> importService.importCsv(new StringReader(csv), "CMDB", false))
                .isInstanceOfSatisfying(InventoryImportException.class, e -> assertThat(e.getErrors()).containsExactly(
                        "Line 3: unknown provider_type \"TOASTER\"",
                        "Line 4: physical_sockets must be a whole number from 1 to 4096, not \"two\"",
                        "Line 4: VSPHERE 4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c appears more than once"));
        assertThat(inventoryRepository.count()).isZero();
        assertThatThrownBy(() -> importService.importCsv(new StringReader("host,rack\nesx-1,3\n"), "CMDB", false))
                .isInstanceOf(InventoryImportException.class);
    }

    private Map<String, NodeMetricsSnapshot> nodes() {
        return nodeRepository.findBySnapshotId(snapshot.getId()).stream()
                .collect(Collectors.toMap(NodeMetricsSnapshot::getNodeName, Function.identity()));
    }
}
