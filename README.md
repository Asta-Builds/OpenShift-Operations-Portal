# OpenShift Operations Portal

An enterprise platform providing unified fleet visibility, licensing audit, owner-aware cost attribution, and predictive resource forecasting across multiple **Red Hat Advanced Cluster Management (ACM) Hubs**.

---

## Key Capabilities

* **Decoupled Snapshot Ingestion:** Collects periodic cluster snapshots without continuously querying or degrading production clusters.
* **Red Hat Licensing Core Counting:** Automates vCPU vs. physical socket/core calculation, worker vs. master node distinction, and compliance auditing.
* **Predictive Resource Forecasting:** Rolling 30, 60, and 90-day linear regression models projecting future core and memory consumption.
* **Owner-Aware Reporting:** Maps infrastructure and namespaces to enterprise teams and cost centers for granular cost attribution.
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
| `GET` | `/api/v1/clusters` | VIEWER | List all registered clusters with latest metrics and owner details |
| `GET` | `/api/v1/clusters/{id}` | VIEWER | Detailed cluster breakdown, node inventory, and historical snapshot trend |
| `POST` | `/api/v1/clusters/collect` | OPERATOR | Trigger immediate snapshot collection across all ACM Hubs |
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
