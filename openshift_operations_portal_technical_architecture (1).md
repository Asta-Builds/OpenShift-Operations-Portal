# OpenShift Operations Portal — Technical Architecture & Implementation Guide

## 1. Executive Summary & Architecture Overview

Managing multiple OpenShift clusters across an enterprise can quickly become complex, often leading to fragmented visibility across multiple consoles. The **OpenShift Operations Portal** solves this by connecting to multiple **Red Hat Advanced Cluster Management (ACM) Hub** clusters, unifying the entire fleet into a single operational view.

To eliminate performance degradation and avoid adding unnecessary load to production clusters, the portal **does not continuously query live clusters**. Instead, it uses a decoupled snapshot collection pipeline:

$$\text{ACM Hub} \longrightarrow \text{Managed Clusters} \longrightarrow \text{Scheduled Collection} \longrightarrow \text{Snapshots} \longrightarrow \text{Operations Portal}$$

---

## 2. Core Features & Capabilities

* **Fleet Capacity & Utilization:** Aggregates CPU, memory, and storage metrics across all managed clusters.
* **License Core Counting:** Tracks and calculates core consumption for subscription and compliance auditing.
* **Owner-Aware Reporting:** Maps infrastructure and namespaces to designated team owners for granular cost and resource attribution.
* **Customized Reports:** Allows operators to build and export custom operational and compliance reports.
* **Email Service:** Automated dispatch for scheduled reports, threshold alerts, and audit summaries.
* **LDAP Integration:** Enterprise authentication and directory synchronization.
* **Underlying Infrastructure Integration:** Correlates OpenShift worker nodes with underlying virtual or bare-metal host metadata.
* **Resilient Collection:** Built-in error handling and fallback mechanisms to ensure intermittent ACM Hub connectivity doesn't break the snapshot pipeline.
* **Air-Gapped by Design:** Fully containerized architecture capable of running in isolated, offline enterprise environments.
* **Resources Increase Estimate:** Predictive forecasting models estimating future resource and licensing requirements based on historical growth.

---

## 3. Recommended Technology Stack

* **Backend:** Java & Spring Boot (REST APIs, scheduled execution, business logic)
* **Frontend:** Angular or React (Responsive dashboard, fleet overview, report builder)
* **Database:** PostgreSQL or MySQL (Persistent storage for historical snapshots, cluster metadata, and user configurations)
* **Identity & Access Management:** Keycloak (OAuth2/OIDC, LDAP federation, RBAC/ABAC)
* **Messaging / Coordination:** RabbitMQ (Optional, for asynchronous report generation and email dispatch queues)
* **Infrastructure & Deployment:** Docker, Nginx (Reverse proxy), Kubernetes/OpenShift (Hosting environment)
* **Migration Management:** Flyway (Database schema version control)

---

## 4. System Architecture & Component Design

### 4.1. Data Collection & Snapshot Pipeline
* **Collector Worker Service:** A scheduled Spring Boot background service (`@Scheduled`) that executes periodic polling jobs against configured ACM Hub Kubernetes APIs.
* **Resilient Polling Strategy:** Implements exponential backoff and circuit breakers (e.g., Resilience4j) to handle network timeouts or temporary ACM Hub unresponsiveness without stalling the entire fleet collector.
* **Snapshot Persistence:** Raw JSON payloads and parsed metrics are stored as timestamped snapshots in relational tables (`cluster_snapshots`, `node_metrics_snapshots`), ensuring rapid read performance for the frontend dashboard without querying live clusters.

### 4.2. Analytics & Forecasting Engine
* **License Core Aggregator:** Queries snapshot tables to sum up active physical/virtual cores categorized by cluster, environment, and owner tags.
* **Growth Estimator Model:** Analyzes historical snapshot differentials over rolling 30/60/90-day windows to project future resource requirements and cost implications.

### 4.3. Reporting & Notification Service
* **Report Builder:** Generates dynamic tabular and graphical summaries based on custom filter criteria (owner, region, cluster type).
* **Email Dispatcher:** Integrates with SMTP relays to deliver automated reports and threshold alerts on configurable cron schedules.

---

## 5. Database Schema Outline (PostgreSQL)

```sql
-- Clusters registered in the portal
CREATE TABLE clusters (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    acm_hub_ref VARCHAR(255) NOT NULL,
    environment VARCHAR(50),
    owner_team VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Historical snapshots collected periodically
CREATE TABLE cluster_snapshots (
    id BIGSERIAL PRIMARY KEY,
    cluster_id INT REFERENCES clusters(id) ON DELETE CASCADE,
    snapshot_timestamp TIMESTAMP NOT NULL,
    total_cpu_cores INT,
    allocated_cpu_cores INT,
    total_memory_gb NUMERIC(10, 2),
    allocated_memory_gb NUMERIC(10, 2),
    license_cores_count INT,
    raw_payload JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient reporting queries
CREATE INDEX idx_snapshots_cluster_time ON cluster_snapshots(cluster_id, snapshot_timestamp DESC);
```

---

## 6. Security & Air-Gapped Deployment

* **Air-Gapped Compliance:** All frontend assets, backend binaries, and database migrations are packaged into immutable Docker images. No external CDN or internet connection is required at runtime.
* **Identity Management:** Integrates with enterprise LDAP directories via Keycloak to enforce strict Role-Based Access Control (RBAC) and Attribute-Based Access Control (ABAC).
* **Reverse Proxy & TLS:** Nginx acts as the secure reverse proxy terminating TLS and routing traffic internally to the Spring Boot API services.