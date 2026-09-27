-- V5: Optional ACM Observability query endpoint per hub (rbac-query-proxy route), used for allocation metrics

ALTER TABLE acm_hubs ADD COLUMN IF NOT EXISTS observability_url VARCHAR(512);
