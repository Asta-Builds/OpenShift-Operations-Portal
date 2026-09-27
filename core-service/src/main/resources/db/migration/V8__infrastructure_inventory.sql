-- V8: Infrastructure correlation. Which hypervisor or physical machine runs a node comes only from inventory rows
-- (CMDB/CSV import today, vCenter or BareMetalHost later), joined on the node's providerID.

CREATE TABLE infrastructure_inventory (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(30) NOT NULL,               -- CMDB | VCENTER | BAREMETALHOST | SIMULATOR
    provider_type VARCHAR(30) NOT NULL,        -- as parsed from providerIDs: VSPHERE, BAREMETAL, ...
    instance_key VARCHAR(255) NOT NULL,        -- vSphere BIOS UUID, cloud instance id, BareMetalHost namespace/name, ...
    hypervisor_host VARCHAR(255),
    hypervisor_cluster VARCHAR(255),
    datacenter VARCHAR(255),
    physical_sockets INT,                      -- of the machine that runs the node: the host for a VM, the node itself on bare metal
    physical_cores INT,
    threads_per_core INT,
    synced_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_inventory_source_key UNIQUE (source, provider_type, instance_key)
);
CREATE INDEX idx_inventory_lookup ON infrastructure_inventory(provider_type, instance_key);

-- What the providerID says, and what the inventory join found; unknown stays NULL
ALTER TABLE node_metrics_snapshots ADD COLUMN provider_type VARCHAR(30);
ALTER TABLE node_metrics_snapshots ADD COLUMN provider_zone VARCHAR(100);
ALTER TABLE node_metrics_snapshots ADD COLUMN hypervisor_cluster VARCHAR(255);
ALTER TABLE node_metrics_snapshots ADD COLUMN datacenter VARCHAR(255);
ALTER TABLE node_metrics_snapshots ADD COLUMN physical_cores INT;
ALTER TABLE node_metrics_snapshots ADD COLUMN threads_per_core INT;
ALTER TABLE node_metrics_snapshots ADD COLUMN inventory_source VARCHAR(30);   -- NULL when no inventory row matched
ALTER TABLE node_metrics_snapshots ALTER COLUMN sockets DROP DEFAULT;

-- Earlier rows carried host names derived from providerIDs and simulator-made socket counts: neither is known
UPDATE node_metrics_snapshots SET hypervisor_host = NULL, sockets = NULL;
