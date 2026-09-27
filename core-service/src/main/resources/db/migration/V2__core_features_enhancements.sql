-- V2: Storage metrics, Namespaces Owner-Attribution, Saved Reports & License Watermarks

ALTER TABLE cluster_snapshots ADD COLUMN IF NOT EXISTS total_storage_gb NUMERIC(12, 2) DEFAULT 0.00;
ALTER TABLE cluster_snapshots ADD COLUMN IF NOT EXISTS allocated_storage_gb NUMERIC(12, 2) DEFAULT 0.00;

ALTER TABLE node_metrics_snapshots ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);
ALTER TABLE node_metrics_snapshots ADD COLUMN IF NOT EXISTS hypervisor_host VARCHAR(255);
ALTER TABLE node_metrics_snapshots ADD COLUMN IF NOT EXISTS sockets INT DEFAULT 2;

CREATE TABLE IF NOT EXISTS namespaces (
    id UUID PRIMARY KEY,
    cluster_id UUID NOT NULL REFERENCES clusters(id) ON DELETE CASCADE,
    namespace_name VARCHAR(255) NOT NULL,
    owner_team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    cost_center VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cluster_namespace UNIQUE(cluster_id, namespace_name)
);

CREATE TABLE IF NOT EXISTS namespace_snapshots (
    id BIGSERIAL PRIMARY KEY,
    namespace_id UUID NOT NULL REFERENCES namespaces(id) ON DELETE CASCADE,
    snapshot_timestamp TIMESTAMP NOT NULL,
    cpu_request_cores NUMERIC(8, 2) DEFAULT 0.00,
    cpu_limit_cores NUMERIC(8, 2) DEFAULT 0.00,
    memory_request_gb NUMERIC(10, 2) DEFAULT 0.00,
    memory_limit_gb NUMERIC(10, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ns_snapshot_time ON namespace_snapshots(namespace_id, snapshot_timestamp DESC);

CREATE TABLE IF NOT EXISTS saved_reports (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    user_id VARCHAR(100) DEFAULT 'admin',
    report_type VARCHAR(50) NOT NULL,
    parameters_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS license_watermarks (
    id BIGSERIAL PRIMARY KEY,
    watermark_date DATE NOT NULL UNIQUE,
    peak_worker_cores INT NOT NULL,
    licensed_cap_cores INT NOT NULL,
    compliance_breach BOOLEAN DEFAULT FALSE,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
