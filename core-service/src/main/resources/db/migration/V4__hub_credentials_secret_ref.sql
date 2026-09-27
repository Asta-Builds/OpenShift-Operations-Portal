-- V4: Hub credentials live in Kubernetes Secrets; the database keeps only the Secret's name.
-- auth_token only ever held simulator mock tokens (no live ACM client existed yet), so it is dropped outright.

ALTER TABLE acm_hubs ADD COLUMN IF NOT EXISTS credentials_secret_ref VARCHAR(255);
ALTER TABLE acm_hubs DROP COLUMN IF EXISTS auth_token;
