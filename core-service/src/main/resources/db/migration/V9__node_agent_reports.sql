-- V9: Node inventory pushed by the node agent running in each managed cluster (plan decision D2). Only the latest
-- report per cluster is kept; collections copy its nodes into their snapshots while it is fresh.

CREATE TABLE node_agent_reports (
    cluster_name VARCHAR(255) PRIMARY KEY,     -- ManagedCluster name; a report may arrive before any hub lists the cluster
    agent_version VARCHAR(50),
    collected_at TIMESTAMP NOT NULL,           -- when the agent read the nodes, on the agent's clock
    received_at TIMESTAMP NOT NULL,            -- when the portal stored the report; freshness is judged on this
    node_count INT NOT NULL,
    nodes TEXT NOT NULL                        -- JSON array: name, role, cpuCores, memoryGb, providerId
);
