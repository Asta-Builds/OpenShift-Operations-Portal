-- OpenShift Operations Portal V1 Schema Migration

CREATE TABLE IF NOT EXISTS acm_hubs (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    api_url VARCHAR(512) NOT NULL,
    auth_token TEXT,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    last_sync_timestamp TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS teams (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    cost_center VARCHAR(100),
    contact_email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clusters (
    id UUID PRIMARY KEY,
    acm_hub_id UUID REFERENCES acm_hubs(id) ON DELETE SET NULL,
    cluster_name VARCHAR(255) NOT NULL UNIQUE,
    environment VARCHAR(50) NOT NULL,
    owner_team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    infrastructure_type VARCHAR(50) DEFAULT 'BARE_METAL',
    openshift_version VARCHAR(50),
    region VARCHAR(100),
    status VARCHAR(50) DEFAULT 'READY',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cluster_snapshots (
    id BIGSERIAL PRIMARY KEY,
    cluster_id UUID NOT NULL REFERENCES clusters(id) ON DELETE CASCADE,
    snapshot_timestamp TIMESTAMP NOT NULL,
    total_cpu_cores INT NOT NULL DEFAULT 0,
    allocated_cpu_cores INT NOT NULL DEFAULT 0,
    total_memory_gb NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    allocated_memory_gb NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    license_cores_count INT NOT NULL DEFAULT 0,
    total_nodes INT NOT NULL DEFAULT 0,
    worker_nodes INT NOT NULL DEFAULT 0,
    raw_payload TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS node_metrics_snapshots (
    id BIGSERIAL PRIMARY KEY,
    cluster_id UUID NOT NULL REFERENCES clusters(id) ON DELETE CASCADE,
    snapshot_id BIGINT REFERENCES cluster_snapshots(id) ON DELETE CASCADE,
    snapshot_timestamp TIMESTAMP NOT NULL,
    node_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    host_type VARCHAR(50) DEFAULT 'VIRTUAL',
    cpu_cores INT NOT NULL DEFAULT 0,
    memory_gb NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    underlying_host_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reports (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    cron_schedule VARCHAR(100),
    filter_criteria TEXT,
    recipients TEXT,
    last_generated_at TIMESTAMP,
    is_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_snapshots_cluster_time ON cluster_snapshots(cluster_id, snapshot_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_node_snapshots_time ON node_metrics_snapshots(cluster_id, snapshot_timestamp DESC);
