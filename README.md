# OpenShift Operations Portal

An enterprise platform providing unified fleet visibility, licensing audit, owner-aware cost attribution, and predictive resource forecasting across multiple **Red Hat Advanced Cluster Management (ACM) Hubs**.

---

## Key Capabilities

* **Decoupled Snapshot Ingestion:** Collects periodic cluster snapshots without continuously querying or degrading production clusters.
* **Red Hat Licensing Core Counting:** Automates vCPU vs. physical socket/core calculation, worker vs. master node distinction, and compliance auditing.
* **Predictive Resource Forecasting:** Rolling 30, 60, and 90-day linear regression models projecting future core and memory consumption.
* **FinOps & Rightsizing Engine:** Continuously analyzes namespace requests vs actual usage telemetry, quantifies monthly wasted spend, classifies efficiency across 5 operational tiers, and generates validated Kubernetes `ResourceQuota` remediation manifests. ([Read Documentation](docs/FINOPS_RIGHTSIZING_ENGINE.md))
* **FinAI Copilot & Operations Assistant:** Air-gapped heuristic reasoning and intelligent assistant delivering deterministic OpenShift rightsizing analysis, one-click dry-run validation (`--dry-run=server`), automated ArgoCD / Kustomize GitOps manifest packaging, Slack/Teams notification webhooks, and executive PDF reports alongside raw Markdown audit journals. ([Read Documentation](docs/FINAI_COPILOT.md))
* **Interactive "What-If" Capacity & Cost Simulator:** Predictive modeling sandbox evaluating quota rightsizing adoption curves, new workload onboarding headroom checks, cluster decommissioning ROI, and organic fleet growth stress-tests in real time. ([Read Documentation](docs/WHAT_IF_CAPACITY_SIMULATOR.md))
* **Interactive D3.js Multi-Cluster Topology Graph:** Dynamic force-directed network diagram linking ACM Hubs, OpenShift Managed Clusters, Physical Hardware/Worker Nodes, and Application Namespaces with real-time FinOps color grading, physics pinning, and deep-inspection drawer. ([Read Documentation](docs/D3_TOPOLOGY_MAP.md))
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
├── node-agent/                 # Spring Boot agent run in each managed cluster: reports its nodes to the portal
│   └── deploy/                 # Namespace, read-only RBAC and Deployment for one managed cluster
├── frontend/                   # Angular 18 Single Page Dashboard
│   ├── src/app/pages/          # Overview, Clusters, Licensing, Forecast, Reports, ACM Hubs, Simulator
│   ├── src/app/shared/         # Pure Lucide SVG Icons Component
│   ├── src/app/services/       # PortalService HTTP Client
│   ├── nginx/                  # Nginx config template: serves the app on 8080, proxies /api/v1 to PORTAL_API_URL
│   ├── Dockerfile              # Multi-stage container build with unprivileged Nginx
│   └── package.json            # Node / Angular configuration
├── docker-compose.yml          # Local sandbox (PostgreSQL, RabbitMQ, Mailpit, OpenLDAP, Keycloak, Backend, Frontend)
├── keycloak/                   # Sandbox realm imported by Keycloak (clients, roles, LDAP federation)
│   └── themes/portal/          # Login theme matching the portal UI (the realm's loginTheme)
├── ldap/                       # Sandbox directory: users and portal groups
├── openshift/                  # OpenShift deployment (see openshift/README.md)
│   ├── base/                   # Core service and UI: Deployments, Services, Route, NetworkPolicies
│   ├── components/             # Optional PostgreSQL and RabbitMQ
│   ├── overlays/example/       # One environment's settings, images, Route host and hub credentials
│   ├── acm/                    # Hub read-only identity and the ACM Policy installing the node agent everywhere
│   ├── pipeline/               # Tekton v1 unit test pipeline for OpenShift Pipelines
│   └── validate.sh             # Schema and consistency checks of all of the above
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

### 3. Run Everything with Docker Compose (PostgreSQL, RabbitMQ, Mailpit, OpenLDAP, Keycloak, Backend & Frontend)

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

Report schedule and alert emails land in Mailpit at `http://localhost:8025`; alerts go to `platform-ops@openshift-portal.local`. The Keycloak admin console is at `http://localhost:8081` (`admin` / `admin`). For scripts, the sandbox realm has a `portal-cli` client that accepts the password grant:

```powershell
curl.exe -d grant_type=password -d client_id=portal-cli -d username=bob -d password=bob-sandbox `
  http://localhost:8081/realms/openshift-portal/protocol/openid-connect/token
```

To run the sandbox without sign-in, set `OPENSHIFT_PORTAL_SECURITY_ENABLED: "false"` on `core-service` in `docker-compose.yml`. The `dev` profile (sections 1 and 2) runs without sign-in by default.

### Connecting a real ACM hub

With the simulator off (`OPENSHIFT_PORTAL_SIMULATOR_ENABLED=false`, the `prod` default) the portal reads hubs through the Kubernetes API:

1. Create a read-only token on the hub (it must be able to list `managedclusters`) and put it in a Kubernetes Secret with key `token` (plus `ca.crt` if the hub uses a private CA).
2. Mount the Secret into the portal at `/var/run/secrets/acm-hubs/<secret-name>/` (directory configurable with `openshift.portal.acm.credentials-dir`).
3. Register the hub as an ADMIN on the **ACM Hubs** page: fill in its API server, the Secret's name and the optional endpoints, and use *Test connection* before saving. The same through the API:

```powershell
curl.exe -X POST http://localhost:4200/api/v1/hubs -H "Authorization: Bearer <token>" -H "Content-Type: application/json" `
  -d '{"name":"hub-east","apiUrl":"https://api.hub-east.example.com:6443","credentialsSecretRef":"hub-east-credentials","observabilityUrl":"https://rbac-query-proxy-open-cluster-management-observability.apps.hub-east.example.com","searchUrl":"https://search-api-open-cluster-management.apps.hub-east.example.com/searchapi/graphql"}'
```

The next collection registers every `ManagedCluster` it finds. It reads capacity, platform, version and region from the cluster's status and ClusterClaims, and the environment from its `environment` label. Clusters that are not Available are recorded as failed for that run. Hubs do not describe nodes: those come from the [node agent](#node-agent) in each managed cluster, and a live cluster without one shows no nodes or license cores.

The ACM Hubs page (and `GET /api/v1/hubs`) shows each hub's status, whether its token is mounted, its circuit breaker, its cluster count and its last collection with the error, and the history of its collections. Operators can *Test connection* on a registered hub: it checks, in order, that the token can be read, what the hub API returns, and whether Observability and Search answer with data, and says which step fails and why. They can also collect one hub, or all, at once. Manual collections take the same lock as the scheduled ones, so they never overlap on any replica; one requested while another collection runs (or within a minute of a scheduled one starting) is refused with 409. Admins also edit and delete hubs there; deleting a hub also deletes its clusters and their history, so the page asks for the hub's name first.

Both extra endpoints are optional:

* **`observabilityUrl`** (ACM Observability, `rbac-query-proxy` route): per-namespace CPU and memory requests, usage and PVC requests. A cluster's requested CPU, memory and storage are the sums over its namespaces, so team attribution always reconciles with cluster totals. Requests come from the `namespace_cpu:` / `namespace_memory:kube_pod_container_resource_requests:sum` recording rules, which count running and pending pods only. Metric names differ between ACM versions, so every query can be replaced under `openshift.portal.acm.queries.*`; add any metric your hub does not keep to the `observability-metrics-custom-allowlist` ConfigMap.
* **`searchUrl`** (ACM Search GraphQL API; expose the `search-search-api` service with a route): namespace labels, and so ownership. Without it namespaces are still measured but stay Unattributed. If Search fails during a collection, the ownership recorded earlier is kept.

To try this without an ACM hub, [hub-lab/README.md](hub-lab/README.md) builds a local Open Cluster Management hub with two managed clusters and stand-ins for Observability and Search.

### Node agent

License cores, watermarks and infrastructure correlation need each cluster's nodes, which ACM hubs do not provide. The [node agent](node-agent/README.md) runs in every managed cluster, lists its `Node` objects (name, role, CPU and memory capacity, `spec.providerID`) every 5 minutes and sends them to `POST /api/v1/node-reports`. The portal keeps the latest report of each cluster, and every collection copies its nodes into the cluster's snapshot while the report is younger than `openshift.portal.node-agent.max-report-age` (default `PT1H`). After that the cluster shows no nodes rather than nodes that may be gone. `GET /api/v1/node-reports` lists the agents' latest reports.

### License audit

The audit (`GET /api/v1/licensing/audit`, the Licensing page and the `LICENSE_AUDIT` exports) counts worker cores from each cluster's latest snapshot. A cluster without node data (no successful collection yet, no node agent report, or a report older than the max report age) is not counted as 0 cores: the audit lists it in `clustersWithoutNodeData` with the reason, the exports leave its cores empty, and the cluster pages show "no node data". While any cluster is listed the totals are a lower bound and the status is `INCOMPLETE`, unless the known cores already exceed the cap, which is `BREACH`.

Every collection that stores snapshots raises today's watermark to the fleet's current license cores. The high watermark is the highest daily value over `openshift.portal.licensing.watermark-period-days` (default 365), compared with the contracted `openshift.portal.licensing.licensed-cap-cores` (default 500).

Agents sign in to Keycloak with the client credentials grant. Their client's service account needs the `portal-node-agent` realm role, which allows sending node reports and nothing else. To keep one cluster's agent from reporting another cluster, give each cluster its own client with a hardcoded `portal_cluster` claim set to the cluster name: the portal then refuses reports for any other cluster. The sandbox realm has a `portal-node-agent` client with secret `node-agent-sandbox-secret`.

### Infrastructure inventory

Which hypervisor host, cluster and datacenter run a node, and the sockets, cores and threads of that machine, come only from the infrastructure inventory; the portal never derives them from a providerID. Each node's `spec.providerID` is parsed into a platform and an instance key (vSphere BIOS UUID, AWS instance id, Azure resource id, GCP project/zone/name, OpenStack server UUID, Metal3 `namespace/host`, ...), and matched to inventory rows with the same key. Nodes without a row are shown as "not in inventory"; public cloud instances need none.

Load the inventory as CSV, from a CMDB export or an asset database, either as an ADMIN with `POST /api/v1/inventory/import?source=CMDB&replace=true` (body `text/csv`) or by dropping `<source>.csv` files into `openshift.portal.inventory.import-dir`, which is read every hour. Columns (header row): `provider_id`, or `provider_type` and `instance_key`, then optionally `hypervisor_host`, `hypervisor_cluster`, `datacenter`, `physical_sockets`, `physical_cores`, `threads_per_core`. A file with any error is rejected whole. For vSphere, use the VM's BIOS UUID as it appears in the node's providerID.

```csv
provider_id,hypervisor_host,hypervisor_cluster,datacenter,physical_sockets,physical_cores,threads_per_core
vsphere://4237c5f4-2a4b-d3c9-1b6e-6e1f2d3a4b5c,esx-07.fra.corp,vsan-prod,dc-frankfurt,2,48,2
baremetalhost:///openshift-machine-api/rack3-host7/5d1a8b3c-7e2f-4a6b-9c0d-1e2f3a4b5c6d,,,dc-frankfurt,2,32,2
```

### Scheduled reports and alerts

Admins create report schedules on the Reports page or with `POST /api/v1/reports`: a title, the report (`FLEET_CAPACITY`, `LICENSE_AUDIT` or `COST_ATTRIBUTION`), a PDF or CSV attachment, a five-field cron schedule in the portal's time zone (`0 7 * * MON` is Mondays at 07:00) and the recipients. Every minute one replica starts the schedules that are due; one missed while the portal was down runs once when it is back. "Send now" (`POST /api/v1/reports/{id}/run`) emails a report at once without changing its schedule. Each email carries the report as an attachment and a short summary of its headline figures.

After every collection the portal also checks two alerts and emails them to `openshift.portal.notifications.alert-recipients` (comma-separated; empty sends none), each at most once a day:

* **License cap exceeded**: the license audit is `BREACH`.
* **Capacity runway**: CPU or memory requests are projected, from the last 30 days of growth, to reach capacity within `openshift.portal.notifications.runway-alert-days` (default 60).

Emails go out through the SMTP server in Spring's `spring.mail.*` settings (`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, and `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` for STARTTLS), from `openshift.portal.notifications.from`. Set `openshift.portal.notifications.portal-url` to add links to the portal. With RabbitMQ enabled (`OPENSHIFT_PORTAL_RABBITMQ_ENABLED`, on in the `prod` profile) report generation runs from `report.generation.queue` and sending from `report.email.queue`, so a slow report never holds up the scheduler; without it both steps run in-process.

Every email is logged with its outcome: `SENT`, `FAILED` with the error, or `NOT_SENT` when no SMTP server is configured. Operators see the log as the Delivery History on the Reports page, or with `GET /api/v1/notifications`, and each schedule shows its last delivery. A failed email is not retried, so recipients never get a report twice; it runs again at its next scheduled time.

### Owner attribution

Each namespace's owner comes from its `openshift.io/owner-team` label and its cost center from `cost-center` (set `openshift.portal.attribution.owner-label` and `cost-center-label` to use your own keys; an organisation-owned prefix is recommended). An owner value maps to a team when it equals the team's name with case and punctuation ignored (`payments-platform` matches "Payments Platform"), or one of the team's aliases. Admins add aliases on the Cost Attribution page or with `POST /api/v1/teams/{id}/aliases`; namespaces carrying the value move to the team immediately.

Attribution values are averages over a period's collections that carried namespace data. A namespace that existed for only part of the period counts for that part, and deleted namespaces are kept with the time they disappeared.

### Roles

Roles build on each other: an ADMIN can do everything an OPERATOR can, and an OPERATOR everything a VIEWER can. In Keycloak they are the realm roles `portal-admin`, `portal-operator` and `portal-viewer`; the sandbox realm maps LDAP groups of the same names to them. Tokens must be issued for the `portal-api` audience. The NODE_AGENT role (`portal-node-agent`) stands apart: it is for node agents' service accounts, only sends node reports, and no other role includes it.

---

## Deploying on OpenShift

[openshift/README.md](openshift/README.md) is the deployment guide. It covers building the images, the Keycloak realm, hub tokens, the Kustomize overlay (core service, UI, Route, NetworkPolicies, and optionally PostgreSQL and RabbitMQ), rolling the node agent out to every managed cluster with an ACM Policy, and the Tekton unit test pipeline. In short:

```bash
oc new-project openshift-operations-portal
oc create secret generic openshift-operations-portal-secrets --from-env-file=secrets.env
oc apply -k openshift/overlays/<your-environment>
```

The UI container listens on port 8080 (unprivileged Nginx) and proxies `/api/v1` to the core service named in `PORTAL_API_URL`; `docker compose` publishes it on `localhost:4200` as before.

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
| `GET` | `/api/v1/hubs/{id}/sync-runs?limit=20` | VIEWER | The hub's most recent collections (up to 100), newest first |
| `POST` | `/api/v1/hubs/{id}/test` | OPERATOR | Test a registered hub: credentials, hub API, Observability and Search, each with its outcome |
| `POST` | `/api/v1/hubs/test` | ADMIN | Test hub settings before registering them (same body as registering) |
| `POST` | `/api/v1/hubs/{id}/collect` | OPERATOR | Collect one hub now; 409 while another collection holds the lock |
| `GET` | `/api/v1/clusters` | VIEWER | List all registered clusters with latest metrics and owner details |
| `GET` | `/api/v1/clusters/{id}` | VIEWER | Detailed cluster breakdown, node inventory, and historical snapshot trend |
| `POST` | `/api/v1/clusters/collect` | OPERATOR | Collect every ACM hub now; 409 while another collection holds the lock |
| `POST` | `/api/v1/node-reports` | NODE_AGENT | A cluster's nodes from its node agent (name, role, CPU, memory, providerID); replaces the cluster's previous report unless that one was read later |
| `GET` | `/api/v1/node-reports` | VIEWER | Latest report of each node agent, with whether a hub reports its cluster and whether collections still use it |
| `GET` | `/api/v1/attribution/teams?from=&to=&environment=` | VIEWER | Requests and usage by team and cost center over inclusive days (default: last 30), with the Unattributed bucket and the cluster-level totals they reconcile with |
| `GET` | `/api/v1/teams` | VIEWER | Teams with their aliases and namespace counts |
| `POST` | `/api/v1/teams` | ADMIN | Create a team (name, cost center, contact email) |
| `POST`/`DELETE` | `/api/v1/teams/{id}/aliases` | ADMIN | Map another owner label value to a team, or remove the mapping (`DELETE .../aliases/{alias}`) |
| `GET` | `/api/v1/infrastructure/topology` | VIEWER | Nodes of the latest snapshots grouped by hypervisor cluster and host, bare-metal machine and cloud zone, with unmatched nodes listed |
| `GET` | `/api/v1/inventory` | VIEWER | Infrastructure inventory rows |
| `POST` | `/api/v1/inventory/import?source=&replace=` | ADMIN | Import inventory CSV (`text/csv`); `replace=true` removes the source's rows missing from the file |
| `DELETE` | `/api/v1/inventory?source=` | ADMIN | Remove every row of one source |
| `GET` | `/api/v1/licensing/audit` | VIEWER | Worker cores, high watermark and compliance status (`COMPLIANT`, `BREACH`, or `INCOMPLETE` while clusters lack node data, which are listed with the reason) |
| `GET` | `/api/v1/forecasting/projection?horizonDays=30` | VIEWER | Predictive resource growth projection (30/60/90 days) |
| `GET` | `/api/v1/reports/export?type=FLEET_CAPACITY` | OPERATOR | Export CSV report (`FLEET_CAPACITY`, `LICENSE_AUDIT`, `COST_ATTRIBUTION`); `/export/pdf` for PDF |
| `GET`/`POST` | `/api/v1/reports/saved` | OPERATOR | The caller's own saved report presets |
| `GET` | `/api/v1/reports` | VIEWER | Report schedules with their next run and last delivery |
| `POST` | `/api/v1/reports` | ADMIN | Create a report schedule (title, report type, `PDF`/`CSV`, five-field cron, recipients) |
| `PATCH`/`DELETE` | `/api/v1/reports/{id}` | ADMIN | Change (title, format, cron, recipients, `enabled`) or delete a schedule; its delivery history is kept |
| `POST` | `/api/v1/reports/{id}/run` | ADMIN | Email the report now, without changing its schedule |
| `GET` | `/api/v1/notifications?limit=50` | OPERATOR | Delivery history: every report email and alert with its outcome, newest first |
| `GET` | `/api/v1/finops/rightsizing?from=&to=` | VIEWER | FinOps rightsizing recommendations, wasted spend, and efficiency tiers |
| `GET` | `/api/v1/finops/summary` | VIEWER | Executive FinOps summary (total waste, potential monthly savings, top namespaces) |
| `POST` | `/api/v1/finops/ai/query` | VIEWER | FinAI Copilot natural language queries, heuristic diagnosis, and `oc` CLI generator |
| `GET` | `/api/v1/finops/ai/quick-prompts` | VIEWER | Predefined FinAI quick prompts organized by operational category |
| `POST` | `/api/v1/finops/ai/dry-run` | OPERATOR | Server-side dry-run validation (`--dry-run=server`) of remediation manifests |
| `POST` | `/api/v1/finops/ai/gitops-manifest` | OPERATOR | Generate ArgoCD Application CRD and Kustomize bundle ready for GitOps commit |
| `POST` | `/api/v1/finops/ai/notify` | OPERATOR | Dispatch FinOps diagnosis to Slack Block Kit or Microsoft Teams webhook |
| `POST` | `/api/v1/finops/ai/export-pdf` | VIEWER | Generate executive A4 PDF report with Red Hat OpenShift branding |
| `POST` | `/api/v1/simulator/fault?fail=true` | ADMIN | Inject a one-off simulated ACM connection failure (simulator only) |
| `POST` | `/api/v1/simulator/outage?hub=...&down=true` | ADMIN | Start or end a simulated outage of one hub (simulator only) |

---

## Testing & Quality Gate

Run the automated test suite:

```powershell
cd core-service
.\mvnw.cmd test
cd ..\node-agent
.\mvnw.cmd test
```

Check the OpenShift manifests (needs `kustomize`, `kubeconform`, `python3` and PyYAML) with `./openshift/validate.sh`.
