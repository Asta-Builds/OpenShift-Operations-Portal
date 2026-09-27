# OpenShift Operations Portal

An enterprise platform providing unified fleet visibility, licensing audit, owner-aware cost attribution, and predictive resource forecasting across multiple **Red Hat Advanced Cluster Management (ACM) Hubs**.

---

## Key Capabilities

* **Decoupled Snapshot Ingestion:** Collects periodic cluster snapshots without continuously querying or degrading production clusters.
* **Red Hat Licensing Core Counting:** Automates vCPU vs. physical socket/core calculation, worker vs. master node distinction, and compliance auditing.
* **Predictive Resource Forecasting:** Rolling 30, 60, and 90-day linear regression models projecting future core and memory consumption.
* **Owner-Aware Reporting:** Attributes namespace requests and usage to teams and cost centers from namespace owner labels; namespaces without a recognised owner stay "Unattributed" and are never charged to the cluster owner.
* **Resilient ACM Polling:** A circuit breaker and exponential-backoff retry per hub, sync-run history, and scheduler locks so one failing hub never stops the others.
* **Enterprise Sign-in:** Keycloak (OIDC, authorization code + PKCE) federating LDAP / Active Directory; directory groups map to the ADMIN, OPERATOR and VIEWER roles.
* **Air-Gapped by Design:** Zero runtime external dependencies or CDN calls; packaged for offline enterprise data centers.

---

## Architecture

```
[ACM Hub 1] -----\
                  +---> [Resilient Collector Service] ---> [PostgreSQL 15+ (Snapshots & JSONB)]
[ACM Hub 2] -----/           (Resilience4j)                             |
                                                                        v
                                                          [Analytics & Forecasting Engine]
                                                                        |
                                                                        v
                                                            [REST APIs & Report Exporter]
                                                                        ^
                                                                        |
                                                    [Angular Dashboard (Air-Gapped UI)]
```

---

## Directory Structure

```
├── core-service/               # Spring Boot 3 Java Backend
│   ├── src/main/java/          # Application, Controllers, Services, Entities, Repositories
│   ├── src/main/resources/     # application.yml, Flyway migrations (db/migration/)
│   ├── src/test/java/          # Comprehensive JUnit 5 & Mockito test suite
│   ├── Dockerfile              # Multi-stage container build
│   └── pom.xml                 # Maven build definition
├── frontend/                   # Angular 18 Single Page Dashboard
│   ├── src/app/pages/          # Overview, Clusters, Licensing, Forecast, Reports, Simulator
│   ├── src/app/shared/         # Pure Lucide SVG Icons Component
│   ├── src/app/services/       # PortalService HTTP Client
│   ├── Dockerfile              # Multi-stage container build with Nginx
│   └── package.json            # Node / Angular configuration
├── docker-compose.yml          # Local sandbox (PostgreSQL, RabbitMQ, OpenLDAP, Keycloak, Backend, Frontend)
├── keycloak/                   # Sandbox realm imported by Keycloak (clients, roles, LDAP federation)
│   └── themes/portal/          # Login theme matching the portal UI (the realm's loginTheme)
├── ldap/                       # Sandbox directory: users and portal groups
├── nginx/                      # Nginx reverse proxy configuration for air-gapped web bundle
│   └── nginx.conf
├── openshift/                  # Kubernetes & OpenShift deployment manifests
│   ├── deployment.yaml
│   └── service-and-route.yaml
└── openshift_operations_portal_technical_architecture (1).md
```

---

## Quick Start (Local Development)

### 1. Run Core Service with Dev Profile (Embedded DB + Simulator)

The `dev` profile runs with an embedded H2 database (in PostgreSQL mode) and automatically pre-seeds a realistic multi-cluster enterprise fleet.

```powershell
cd core-service
.\mvnw.cmd spring-boot:run
```

The service will start at `http://localhost:8080/api/v1`.

### 2. Run Angular Frontend (Development Server)

The Angular frontend includes proxy routing to forward all `/api` calls directly to the Spring Boot backend on port 8080.

```powershell
cd frontend
npm start
```

The portal UI will be accessible at `http://localhost:4200`.

### 3. Run Everything with Docker Compose (PostgreSQL, RabbitMQ, OpenLDAP, Keycloak, Backend & Frontend)

```powershell
docker compose up -d
```

Open `http://localhost:4200` and sign in with a sandbox directory user (defined in `ldap/bootstrap.ldif`, for local use only):

| User | Password | LDAP group | Portal role |
| :--- | :--- | :--- | :--- |
| `alice` | `alice-sandbox` | `portal-admin` | ADMIN |
| `bob` | `bob-sandbox` | `portal-operator` | OPERATOR |
| `carol` | `carol-sandbox` | `portal-viewer` | VIEWER |
| `dave` | `dave-sandbox` | none | no access |

The Keycloak admin console is at `http://localhost:8081` (`admin` / `admin`). For scripts, the sandbox realm has a `portal-cli` client that accepts the password grant:

```powershell
curl.exe -d grant_type=password -d client_id=portal-cli -d username=bob -d password=bob-sandbox `
  http://localhost:8081/realms/openshift-portal/protocol/openid-connect/token
```

To run the sandbox without sign-in, set `OPENSHIFT_PORTAL_SECURITY_ENABLED: "false"` on `core-service` in `docker-compose.yml`. The `dev` profile (sections 1 and 2) runs without sign-in by default.

### Connecting a real ACM hub

With the simulator off (`OPENSHIFT_PORTAL_SIMULATOR_ENABLED=false`, the `prod` default) the portal reads hubs through the Kubernetes API:

1. Create a read-only token on the hub (it must be able to list `managedclusters`) and put it in a Kubernetes Secret with key `token` (plus `ca.crt` if the hub uses a private CA).
2. Mount the Secret into the portal at `/var/run/secrets/acm-hubs/<secret-name>/` (directory configurable with `openshift.portal.acm.credentials-dir`).
3. Register the hub as an ADMIN:

```powershell
curl.exe -X POST http://localhost:4200/api/v1/hubs -H "Authorization: Bearer <token>" -H "Content-Type: application/json" `
  -d '{"name":"hub-east","apiUrl":"https://api.hub-east.example.com:6443","credentialsSecretRef":"hub-east-credentials","observabilityUrl":"https://rbac-query-proxy-open-cluster-management-observability.apps.hub-east.example.com","searchUrl":"https://search-api-open-cluster-management.apps.hub-east.example.com/searchapi/graphql"}'
```

The next collection registers every `ManagedCluster` it finds. It reads capacity, platform, version and region from the cluster's status and ClusterClaims, and the environment from its `environment` label. Clusters that are not Available are recorded as failed for that run. Node inventory is not read yet, so live clusters show no nodes or license cores.

Both extra endpoints are optional:

* **`observabilityUrl`** (ACM Observability, `rbac-query-proxy` route): per-namespace CPU and memory requests, usage and PVC requests. A cluster's requested CPU, memory and storage are the sums over its namespaces, so team attribution always reconciles with cluster totals. Requests come from the `namespace_cpu:` / `namespace_memory:kube_pod_container_resource_requests:sum` recording rules, which count running and pending pods only. Metric names differ between ACM versions, so every query can be replaced under `openshift.portal.acm.queries.*`; add any metric your hub does not keep to the `observability-metrics-custom-allowlist` ConfigMap.
* **`searchUrl`** (ACM Search GraphQL API; expose the `search-search-api` service with a route): namespace labels, and so ownership. Without it namespaces are still measured but stay Unattributed. If Search fails during a collection, the ownership recorded earlier is kept.

To try this without an ACM hub, [hub-lab/README.md](hub-lab/README.md) builds a local Open Cluster Management hub with two managed clusters and stand-ins for Observability and Search.

### Infrastructure inventory

Which hypervisor host, cluster and datacenter run a node, and the sockets, cores and threads of that machine, come only from the infrastructure inventory; the portal never derives them from a providerID. Each node's `spec.providerID` is parsed into a platform and an instance key (vSphere BIOS UUID, AWS instance id, Azure resource id, GCP project/zone/name, OpenStack server UUID, Metal3 `namespace/host`, ...), and matched to inventory rows with the same key. Nodes without a row are shown as "not in inventory"; public cloud instances need none.

Load the inventory as CSV, from a CMDB export or an asset database, either as an ADMIN with `POST /api/v1/inventory/import?source=CMDB&replace=true` (body `text/csv`) or by dropping `<source>.csv` files into `openshift.portal.inventory.import-dir`, which is read every hour. Columns (header row): `provider_id`, or `provider_type` and `instance_key`, then optionally `hypervisor_host`, `hypervisor_cluster`, `datacenter`, `physical_sockets`, `physical_cores`, `threads_per_core`. A file with any error is rejected whole. For vSphere, use the VM's BIOS UUID as it appears in the node's providerID.

```csv
provider_id,hypervisor_host,hypervisor_cluster,datacenter,physical_sockets,physical_cores,threads_per_core
vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c,esx-07.fra.corp,vsan-prod,dc-frankfurt,2,48,2
baremetalhost:///openshift-machine-api/rack3-host7/5d1a8b3c-7e2f-4a6b-9c0d-1e2f3a4b5c6d,,,dc-frankfurt,2,32,2
```

### Owner attribution

Each namespace's owner comes from its `openshift.io/owner-team` label and its cost center from `cost-center` (set `openshift.portal.attribution.owner-label` and `cost-center-label` to use your own keys; an organisation-owned prefix is recommended). An owner value maps to a team when it equals the team's name with case and punctuation ignored (`payments-platform` matches "Payments Platform"), or one of the team's aliases. Admins add aliases on the Cost Attribution page or with `POST /api/v1/teams/{id}/aliases`; namespaces carrying the value move to the team immediately.

Attribution values are averages over a period's collections that carried namespace data. A namespace that existed for only part of the period counts for that part, and deleted namespaces are kept with the time they disappeared.

### Roles

Roles build on each other: an ADMIN can do everything an OPERATOR can, and an OPERATOR everything a VIEWER can. In Keycloak they are the realm roles `portal-admin`, `portal-operator` and `portal-viewer`; the sandbox realm maps LDAP groups of the same names to them. Tokens must be issued for the `portal-api` audience.

---

## Core REST API Reference

| Method | Endpoint | Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/auth/config` | public | Whether sign-in is required, and the issuer and client to use |
| `GET` | `/api/v1/auth/me` | signed in | Current user and portal roles |
| `GET` | `/api/v1/fleet/overview` | VIEWER | Aggregated fleet cores, memory, utilization %, and cluster distributions |
| `GET` | `/api/v1/hubs` | VIEWER | ACM hubs with status, failures in a row, circuit breaker state and latest sync run |
| `POST` | `/api/v1/hubs` | ADMIN | Register an ACM hub (name, API URL, credentials Secret name, optional Observability and Search URLs) |
| `PATCH` | `/api/v1/hubs/{id}` | ADMIN | Change a hub's API URL, credentials Secret, or Observability / Search URLs (empty string removes an optional URL), keeping its clusters and history |
| `DELETE` | `/api/v1/hubs/{id}` | ADMIN | Remove a hub with its clusters and snapshots |
| `GET` | `/api/v1/clusters` | VIEWER | List all registered clusters with latest metrics and owner details |
| `GET` | `/api/v1/clusters/{id}` | VIEWER | Detailed cluster breakdown, node inventory, and historical snapshot trend |
| `POST` | `/api/v1/clusters/collect` | OPERATOR | Trigger immediate snapshot collection across all ACM Hubs |
| `GET` | `/api/v1/attribution/teams?from=&to=&environment=` | VIEWER | Requests and usage by team and cost center over inclusive days (default: last 30), with the Unattributed bucket and the cluster-level totals they reconcile with |
| `GET` | `/api/v1/teams` | VIEWER | Teams with their aliases and namespace counts |
| `POST` | `/api/v1/teams` | ADMIN | Create a team (name, cost center, contact email) |
| `POST`/`DELETE` | `/api/v1/teams/{id}/aliases` | ADMIN | Map another owner label value to a team, or remove the mapping (`DELETE .../aliases/{alias}`) |
| `GET` | `/api/v1/infrastructure/topology` | VIEWER | Nodes of the latest snapshots grouped by hypervisor cluster and host, bare-metal machine and cloud zone, with unmatched nodes listed |
| `GET` | `/api/v1/inventory` | VIEWER | Infrastructure inventory rows |
| `POST` | `/api/v1/inventory/import?source=&replace=` | ADMIN | Import inventory CSV (`text/csv`); `replace=true` removes the source's rows missing from the file |
| `DELETE` | `/api/v1/inventory?source=` | ADMIN | Remove every row of one source |
| `GET` | `/api/v1/licensing/audit` | VIEWER | Core counting and subscription compliance audit |
| `GET` | `/api/v1/forecasting/projection?horizonDays=30` | VIEWER | Predictive resource growth projection (30/60/90 days) |
| `GET` | `/api/v1/reports/export?type=FLEET_CAPACITY` | OPERATOR | Export CSV report (`FLEET_CAPACITY`, `LICENSE_AUDIT`, `COST_ATTRIBUTION`); `/export/pdf` for PDF |
| `GET`/`POST` | `/api/v1/reports/saved` | OPERATOR | The caller's own saved report presets |
| `POST` | `/api/v1/reports` | ADMIN | Create a scheduled report definition |
| `POST` | `/api/v1/simulator/fault?fail=true` | ADMIN | Inject a one-off simulated ACM connection failure (simulator only) |
| `POST` | `/api/v1/simulator/outage?hub=...&down=true` | ADMIN | Start or end a simulated outage of one hub (simulator only) |

---

## Testing & Quality Gate

Run the automated test suite:

```powershell
cd core-service
.\mvnw.cmd test
```
