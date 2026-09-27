package com.openshift.portal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openshift.portal.acm.NodeObservation;
import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.Cluster;
import com.openshift.portal.domain.entity.NodeAgentReport;
import com.openshift.portal.dto.NodeAgentStatusDto;
import com.openshift.portal.dto.NodeReportReceiptDto;
import com.openshift.portal.dto.NodeReportRequest;
import com.openshift.portal.repository.ClusterRepository;
import com.openshift.portal.repository.NodeAgentReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the latest node report of each cluster's node agent and hands its nodes to collections. Hubs only describe
 * clusters as a whole, so these reports are where live clusters' nodes, and so their license cores, come from.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NodeAgentReportService {

    private static final TypeReference<List<NodeObservation>> NODE_LIST = new TypeReference<>() {
    };

    private final NodeAgentReportRepository reportRepository;
    private final ClusterRepository clusterRepository;
    private final ObjectMapper objectMapper;
    private final AcmProperties properties;

    /** Stores the report unless the portal already holds one the agent read later (a delayed retry). */
    @Transactional
    public NodeReportReceiptDto record(NodeReportRequest request) {
        String clusterName = request.getClusterName();
        LocalDateTime collectedAt = LocalDateTime.ofInstant(request.getCollectedAt(), ZoneId.systemDefault());
        List<NodeObservation> nodes = request.getNodes().stream()
                .map(node -> new NodeObservation(node.getName(), node.getRole(), node.getCpuCores(),
                        node.getMemoryGb().setScale(2, RoundingMode.HALF_UP), node.getProviderId()))
                .toList();
        boolean registered = clusterRepository.findByClusterName(clusterName).isPresent();

        NodeAgentReport report = reportRepository.findById(clusterName)
                .orElseGet(() -> NodeAgentReport.builder().clusterName(clusterName).build());
        if (report.getCollectedAt() != null && collectedAt.isBefore(report.getCollectedAt())) {
            log.info("Ignoring node report of cluster {} read at {}: the report read at {} is newer", clusterName,
                    collectedAt, report.getCollectedAt());
            return new NodeReportReceiptDto(clusterName, report.getNodeCount(), registered);
        }
        report.setAgentVersion(request.getAgentVersion());
        report.setCollectedAt(collectedAt);
        report.setReceivedAt(LocalDateTime.now());
        report.setNodeCount(nodes.size());
        report.setNodes(write(nodes));
        reportRepository.save(report);
        if (!registered) {
            log.warn("Node agent reported cluster {}, which no ACM hub has reported; check the agent's cluster name",
                    clusterName);
        }
        return new NodeReportReceiptDto(clusterName, nodes.size(), registered);
    }

    /**
     * Nodes of the cluster's latest report, or empty when it has none younger than the max report age. Stale nodes
     * are left out on purpose: a cluster whose agent stopped reporting shows no node data instead of old nodes.
     */
    @Transactional(readOnly = true)
    public Optional<List<NodeObservation>> freshNodes(String clusterName) {
        return reportRepository.findById(clusterName)
                .filter(report -> {
                    boolean fresh = isFresh(report);
                    if (!fresh) {
                        log.warn("Latest node report of cluster {} was received at {}, older than {}; its nodes are not used",
                                clusterName, report.getReceivedAt(), properties.getNodeAgent().getMaxReportAge());
                    }
                    return fresh;
                })
                .map(report -> read(report.getNodes()));
    }

    @Transactional(readOnly = true)
    public List<NodeAgentStatusDto> statuses() {
        Set<String> clusterNames = clusterRepository.findAll().stream()
                .map(Cluster::getClusterName)
                .collect(Collectors.toSet());
        return reportRepository.findAll(Sort.by("clusterName")).stream()
                .map(report -> new NodeAgentStatusDto(report.getClusterName(), report.getAgentVersion(),
                        report.getCollectedAt(), report.getReceivedAt(), report.getNodeCount(),
                        clusterNames.contains(report.getClusterName()), isFresh(report)))
                .toList();
    }

    private boolean isFresh(NodeAgentReport report) {
        return report.getReceivedAt().isAfter(LocalDateTime.now().minus(properties.getNodeAgent().getMaxReportAge()));
    }

    private String write(List<NodeObservation> nodes) {
        try {
            return objectMapper.writeValueAsString(nodes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize node report", e);
        }
    }

    private List<NodeObservation> read(String json) {
        try {
            return objectMapper.readValue(json, NODE_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored node report cannot be read", e);
        }
    }
}
