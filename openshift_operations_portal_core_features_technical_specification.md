# OpenShift Operations Portal: Core Features Technical Specification

This document provides a comprehensive technical deep-dive into the implementation architecture, data models, and operational logic for the 10 core features of the OpenShift Operations Portal.

---

## 1. Fleet Capacity & Utilization

### Objective
Provide real-time and historical visibility into CPU, memory, and storage allocation versus capacity across all managed OpenShift clusters without generating redundant telemetry load on production endpoints.

### Technical Implementation
* **Data Sources:** The collector worker queries the Red Hat Advanced Cluster Management (ACM) Hub MultiClusterObservability and Kubernetes Metrics API endpoints.
* **Metric Extraction:** 
  * Total capacity: Sum of allocatable resources across worker nodes (`kube_node_status_allocatable`).
  * Utilization: Actual resource consumption gathered via Prometheus scrapers integrated within ACM (`container_cpu_usage_seconds_total`, `container_memory_working_set_bytes`).
* **Database Schema Integration:** Stored within `cluster_snapshots` using JSONB payloads to accommodate varying node topologies and custom resource definitions.
* **Aggregation Logic:** The backend calculates fleet-wide utilization percentages using the formula:
  $$U_{\text{fleet}} = \frac{\sum_{i=1}^{n} \text{Allocated}_i}{\sum_{i=1}^{n} \text{Capacity}_i} \times 100$$

---

## 2. License Core Counting

### Objective
Accurately track, audit, and project core consumption across physical and virtual worker nodes for subscription compliance reporting.

### Technical Implementation
* **Core Identification:** Scrapes worker node object specifications (`status.capacity.cpu`) and filters out control plane nodes where subscription billing rules exempt them or treat them differently.
* **Socket vs. Core Mapping:** Multiplies physical socket counts by hyperthreading ratios where required by enterprise software agreements.
* **Audit Trail:** Every collection cycle records core counts into historical tables, generating daily snapshots to track license watermarks.
* **Compliance Alerting:** Triggers internal notifications if active core counts exceed predetermined enterprise license thresholds.

---

## 3. Owner-Aware Reporting

### Objective
Map infrastructure resources (namespaces, projects, and clusters) to designated team owners for granular cost allocation and accountability.

### Technical Implementation
* **Metadata Tagging:** Enforces a standardized labeling convention across OpenShift namespaces (e.g., `openshift.io/owner-team`, `cost-center`).
* **Mapping Engine:** The backend synchronization service inspects namespace labels during scheduled snapshot ingestion and maps them to the `clusters` and `namespaces` relational tables.
* **Attribution View:** Generates aggregated reports segmented by team owner, enabling department-level utilization summaries.

---

## 4. Customized Reports

### Objective
Empower operators and managers to build, save, and export tailored operational and compliance reports.

### Technical Implementation
* **Dynamic Query Builder:** A REST API endpoint accepting filter parameters (time range, cluster environment, owner team, metric type) that constructs parameterized SQL queries dynamically against the snapshot store.
* **Export Formats:** Supports programmatic rendering to CSV (via Apache Commons CSV) and PDF (via OpenPDF/Apache FPDF equivalents).
* **Saved Reports:** User-defined report configurations are saved in a `saved_reports` table linked to user IDs for rapid one-click regeneration.

---

## 5. Email Service

### Objective
Automate the dispatch of scheduled operational reports, license alerts, and threshold notifications.

### Technical Implementation
* **Asynchronous Queueing:** Uses RabbitMQ message brokers to decouple report generation from email dispatch, preventing thread-blocking during heavy report rendering.
* **SMTP Integration:** Configurable via environment variables (`SMTP_HOST`, `SMTP_PORT`, `SMTP_AUTH`) to securely relay through enterprise mail servers.
* **Templating Engine:** Thymeleaf HTML email templates embedded with dynamic variables for clean, professional executive summaries.

---

## 6. LDAP Integration

### Objective
Integrate with enterprise identity providers for secure authentication and strict access control.

### Technical Implementation
* **Keycloak Federation:** Keycloak acts as the OpenID Connect (OIDC) identity broker, federating authentication requests directly to corporate LDAP/Active Directory trees.
* **Role Mapping:** Maps LDAP security groups directly to portal roles (`ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_VIEWER`).
* **Session Management:** Stateless JSON Web Tokens (JWT) issued upon successful authentication, validated at the API Gateway / Nginx reverse proxy layer.

---

## 7. Underlying Infrastructure Integration

### Objective
Correlate OpenShift worker nodes with underlying virtual machine hypervisors or bare-metal host metadata.

### Technical Implementation
* **Metadata Annotation:** Pulls provider-specific annotations (`spec.providerID`) from OpenShift Node objects.
* **Infrastructure Correlation:** Joins node identifiers with hypervisor inventory tables (e.g., VMware vCenter or bare-metal asset management DBs) during ingestion.
* **Topology View:** Displays physical-to-virtual mappings in the portal UI, allowing operators to diagnose hardware-level bottlenecks affecting cluster stability.

---

## 8. Resilient Collection

### Objective
Ensure intermittent network failures or ACM Hub unresponsiveness do not stall the collection pipeline or corrupt historical data.

### Technical Implementation
* **Circuit Breaker Pattern:** Implements Resilience4j circuit breakers around external ACM API calls. If an ACM Hub fails $k$ consecutive times, the circuit opens, failing fast and preventing thread exhaustion.
* **Exponential Backoff:** Failed collection jobs retry with calculated backoff intervals:
  $$T_{\text{retry}} = T_{\text{base}} \times 2^{\text{attempt}}$$
* **Graceful Degradation:** If one ACM Hub is unreachable, data collection for other healthy hubs continues uninterrupted, logging the partial sync status.

---

## 9. Air-Gapped by Design

### Objective
Operate fully within isolated, offline enterprise environments with zero external internet dependencies.

### Technical Implementation
* **Container Packaging:** All frontend assets, backend binaries, and database migration scripts are packaged into self-contained Docker images.
* **Static Asset Bundling:** JavaScript and CSS frameworks (React/Angular) are bundled locally; no external CDN links or font requests are made at runtime.
* **Registry Sync:** Designed to be mirrored directly into internal enterprise container registries (e.g., Red Hat Quay or OpenShift internal registry).

---

## 10. Resources Increase Estimate

### Objective
Provide predictive forecasting models to estimate future resource and licensing requirements based on historical growth trends.

### Technical Implementation
* **Regression Modeling:** Analyzes historical snapshot data over rolling 30, 60, and 90-day windows using linear regression to project future capacity utilization:
  $$y = mx + b$$
  where $y$ represents projected resource consumption, $x$ represents time, $m$ is the growth rate coefficient, and $b$ is the baseline capacity.
* **Runway Calculation:** Computes the projected depletion date for available CPU cores and memory limits, alerting administrators when infrastructure expansion is required.