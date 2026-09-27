-- V7: requested CPU keeps fractions of a core. Real clusters request 250m and similar; rounding each cluster to a
-- whole core distorted small clusters and broke reconciliation with namespace requests.
ALTER TABLE cluster_snapshots ALTER COLUMN allocated_cpu_cores SET DATA TYPE NUMERIC(10, 2);
