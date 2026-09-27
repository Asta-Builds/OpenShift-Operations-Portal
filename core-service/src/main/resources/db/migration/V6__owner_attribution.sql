-- V6: Owner-aware reporting (namespace ownership from labels, team aliases, namespace usage and lifecycle)

-- Optional ACM Search GraphQL endpoint per hub; namespace labels (and so ownership) are read from it
ALTER TABLE acm_hubs ADD COLUMN search_url VARCHAR(512);

-- Owner label values that map to a team besides its own name
CREATE TABLE team_aliases (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    alias VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_alias UNIQUE (alias)
);

-- Namespace rows written before this version were simulator placeholders owned by their cluster's team rather
-- than read from labels; nothing else ever wrote them. Remove them so attribution only reflects collected data.
DELETE FROM namespace_snapshots;
DELETE FROM namespaces;

-- owner_label_value and cost_center keep the raw label values, even when they match no team
ALTER TABLE namespaces ADD COLUMN owner_label_value VARCHAR(255);
ALTER TABLE namespaces ADD COLUMN labels_collected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE namespaces ADD COLUMN last_seen_at TIMESTAMP;
ALTER TABLE namespaces ADD COLUMN deleted_at TIMESTAMP;

-- Each namespace snapshot belongs to the cluster snapshot it was collected with; unknown values stay NULL
ALTER TABLE namespace_snapshots ADD COLUMN cluster_snapshot_id BIGINT REFERENCES cluster_snapshots(id) ON DELETE CASCADE;
ALTER TABLE namespace_snapshots ADD COLUMN cpu_usage_cores NUMERIC(8, 2);
ALTER TABLE namespace_snapshots ADD COLUMN memory_usage_gb NUMERIC(10, 2);
ALTER TABLE namespace_snapshots ADD COLUMN pvc_request_gb NUMERIC(12, 2);
ALTER TABLE namespace_snapshots ALTER COLUMN cpu_limit_cores DROP DEFAULT;
ALTER TABLE namespace_snapshots ALTER COLUMN memory_limit_gb DROP DEFAULT;

CREATE INDEX idx_ns_snapshot_cluster_snapshot ON namespace_snapshots(cluster_snapshot_id);
CREATE INDEX idx_ns_snapshot_timestamp ON namespace_snapshots(snapshot_timestamp);
