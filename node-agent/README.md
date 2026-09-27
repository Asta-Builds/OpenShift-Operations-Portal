# Node agent

ACM hubs describe each managed cluster as a whole, but license core counting, watermarks and infrastructure correlation need the cluster's nodes. The node agent is a small Spring Boot service that runs in every managed cluster, lists its `Node` objects through its own API server, and sends them to the portal every 5 minutes.

```
[managed cluster]                                   [hub cluster]
  node-agent --list nodes--> API server
      |
      +--client credentials--> Keycloak
      +--POST /api/v1/node-reports-----------------> portal core-service
                                                      (collections copy the latest fresh report into snapshots)
```

The agent pushes rather than the portal pulling, so the portal needs no credentials or network path into managed clusters, and the agent only needs `get`/`list` on nodes.

## What it sends

For every node: its name, role, CPU capacity in whole cores (rounded up), memory capacity in GiB, and `spec.providerID`. The portal matches the providerID with its [infrastructure inventory](../README.md#infrastructure-inventory) to find the hypervisor host or physical machine.

The role comes from the `node-role.kubernetes.io/*` labels:

| Labels | Role | Counts towards license cores |
| :--- | :--- | :--- |
| `infra` (with or without `worker`) | INFRA | no |
| `master` or `control-plane`, without `worker` | MASTER | no |
| `master` or `control-plane` together with `worker` (compact and single-node clusters, where the control plane runs workloads) | WORKER | yes |
| anything else | WORKER | yes |

Capacity is what the node reports in `status.capacity`, so it is the size of the VM or machine, not what Kubernetes leaves for pods.

## Configuration

Set through environment variables (or the matching `node-agent.*` properties):

| Variable | Default | Meaning |
| :--- | :--- | :--- |
| `CLUSTER_NAME` | required | The cluster's `ManagedCluster` name on its hub. The portal matches reports on it; a report for a name no hub has reported is stored, but the portal answers `registered: false` and the agent logs a warning |
| `PORTAL_URL` | required | Portal API base URL including `/api/v1` |
| `PORTAL_TOKEN_URI` | empty | Keycloak token endpoint. Empty sends no token, which only a portal with security disabled accepts |
| `PORTAL_CLIENT_ID`, `PORTAL_CLIENT_SECRET` | empty | The agent's Keycloak client; required with `PORTAL_TOKEN_URI` |
| `PORTAL_SSL_BUNDLE` | empty | Name of a Spring SSL bundle trusting a private CA (see below); empty uses the JVM's trust store |
| `REPORT_INTERVAL` | `PT5M` | Time between reports. Keep it well under the portal's `openshift.portal.node-agent.max-report-age` (1 hour) |

The agent refuses to start when a required value is missing or malformed.

**Private CA.** Mount the CA certificate (for example from a ConfigMap at `/etc/portal-ca/ca.crt`) and define a PEM bundle; it is used for both the portal and Keycloak:

```yaml
env:
  - name: SPRING_SSL_BUNDLE_PEM_PORTAL_TRUSTSTORE_CERTIFICATE
    value: file:/etc/portal-ca/ca.crt
  - name: PORTAL_SSL_BUNDLE
    value: portal
```

## Keycloak

The agent signs in with the client credentials grant. Its client must be confidential with service accounts enabled, add the `portal-api` audience (an audience mapper, as on the other portal clients), and its service account needs the `portal-node-agent` realm role. That role allows `POST /node-reports` and nothing else: an agent token cannot read dashboards.

One client can serve every cluster. To make sure one cluster's agent cannot report another cluster, give each cluster its own client with a hardcoded claim mapper that puts `portal_cluster` = the cluster name in the access token; the portal then refuses reports for any other cluster with 403.

The sandbox realm ([keycloak/openshift-portal-realm.json](../keycloak/openshift-portal-realm.json)) has a shared `portal-node-agent` client with secret `node-agent-sandbox-secret`, for local use only.

## Deploy

[deploy/node-agent.yaml](deploy/node-agent.yaml) creates, in the managed cluster, the `openshift-portal-node-agent` namespace, a service account with a read-only `nodes` ClusterRole, the credentials Secret, a ConfigMap and the Deployment. Replace the `REPLACE_WITH_*` values, the URLs and the image (a registry your managed clusters can pull from), then:

```bash
oc apply -f deploy/node-agent.yaml
oc -n openshift-portal-node-agent logs deploy/node-agent   # "Reported N nodes of cluster ... to the portal"
```

To roll it out to the whole fleet, wrap the same objects in an ACM Policy placed on the managed clusters and set `CLUSTER_NAME` with the hub template `{{hub .ManagedClusterName hub}}`.

In the portal, `GET /api/v1/node-reports` (VIEWER) lists every agent's latest report with `registered` (a hub reports this cluster) and `fresh` (collections still use it). A cluster whose agent stops reporting shows no nodes once its report is older than the max report age, rather than nodes that may be gone.

## Health

`/actuator/health/liveness` and `/actuator/health/readiness` are the probes. `/actuator/health` also shows a `nodeReport` component: `UNKNOWN` before the first report, `UP` with the time and node count of the last one, or `DOWN` with the error of a failed one. It is not part of the probes, so an unreachable portal never restarts the agent; it just retries at the next interval.

## Build and test

```powershell
.\mvnw.cmd test                              # unit tests, JaCoCo report in target/site/jacoco
docker build -t openshift-portal/node-agent .
```

## Run locally against the hub lab

With the [hub lab](../hub-lab/README.md) and the Docker Compose sandbox running (portal on 8080, Keycloak on 8081), run the agent on your machine against `cluster1`:

```powershell
kind get kubeconfig --name cluster1 | Set-Content -Encoding ascii ..\hub-lab\.state\kube\cluster1-host.yaml
$env:KUBECONFIG = (Resolve-Path ..\hub-lab\.state\kube\cluster1-host.yaml).Path
$env:CLUSTER_NAME = 'cluster1'
$env:PORTAL_URL = 'http://localhost:8080/api/v1'
$env:PORTAL_TOKEN_URI = 'http://localhost:8081/realms/openshift-portal/protocol/openid-connect/token'
$env:PORTAL_CLIENT_ID = 'portal-node-agent'
$env:PORTAL_CLIENT_SECRET = 'node-agent-sandbox-secret'
$env:SERVER_PORT = '8090'   # the portal already uses 8080
.\mvnw.cmd spring-boot:run
```

A kind cluster's only node is labelled `control-plane` without `worker`, so it reports as MASTER and counts no license cores; add worker nodes to the kind cluster to see cores.
