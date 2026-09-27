-- V3: Resilient collection: per-hub sync runs, consecutive failure counter, scheduler locks (ShedLock)

ALTER TABLE acm_hubs ADD COLUMN IF NOT EXISTS consecutive_failures INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS hub_sync_runs (
    id BIGSERIAL PRIMARY KEY,
    hub_id UUID NOT NULL REFERENCES acm_hubs(id) ON DELETE CASCADE,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    clusters_ok INT NOT NULL DEFAULT 0,
    clusters_failed INT NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_hub_sync_runs_hub_time ON hub_sync_runs(hub_id, started_at DESC);

-- Schema of ShedLock's JDBC lock provider
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
