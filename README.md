# OpenShift Operations Portal

An enterprise platform providing unified fleet visibility, licensing audit, owner-aware cost attribution, and predictive resource forecasting across multiple **Red Hat Advanced Cluster Management (ACM) Hubs**.

---

## Key Capabilities

* **Decoupled Snapshot Ingestion:** Collects periodic cluster snapshots without continuously querying or degrading production clusters.
* **Red Hat Licensing Core Counting:** Automates vCPU vs. physical socket/core calculation, worker vs. master node distinction, and compliance auditing.
* **Predictive Resource Forecasting:** Rolling 30, 60, and 90-day linear regression models projecting future core and memory consumption.
* **Owner-Aware Reporting:** Maps infrastructure and namespaces to enterprise teams and cost centers for granular cost attribution.
* **Resilient ACM Polling:** Implements Resilience4j Circuit Breakers, Exponential Backoff Retries, and Fallback handlers for network isolation resilience.
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
├── docker-compose.yml          # Local sandbox (PostgreSQL, RabbitMQ, Keycloak, Backend, Frontend)
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

### 3. Run Everything with Docker Compose (PostgreSQL, RabbitMQ, Keycloak, Backend & Frontend)

```powershell
docker compose up -d
```

---

## Core REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/fleet/overview` | Aggregated fleet cores, memory, utilization %, and cluster distributions |
| `GET` | `/api/v1/clusters` | List all registered clusters with latest metrics and owner details |
| `GET` | `/api/v1/clusters/{id}` | Detailed cluster breakdown, node inventory, and historical snapshot trend |
| `POST` | `/api/v1/clusters/collect` | Trigger immediate snapshot collection across all ACM Hubs |
| `GET` | `/api/v1/licensing/audit` | Comprehensive core counting and subscription compliance audit |
| `GET` | `/api/v1/forecasting/projection?horizonDays=30` | Predictive resource growth projection (30/60/90 days) |
| `GET` | `/api/v1/reports/export?type=FLEET_CAPACITY` | Export CSV report (`FLEET_CAPACITY`, `LICENSE_AUDIT`, `COST_ATTRIBUTION`) |
| `POST` | `/api/v1/simulator/fault?fail=true` | Inject simulated ACM connection failure to test Circuit Breakers |

---

## Testing & Quality Gate

Run the automated test suite:

```powershell
cd core-service
.\mvnw.cmd test
```
