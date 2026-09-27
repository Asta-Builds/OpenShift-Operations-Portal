# Hub lab

A local, real hub for the portal's live client: an [Open Cluster Management](https://open-cluster-management.io/) hub (the upstream of ACM, same `ManagedCluster` and ClusterClaim APIs) and two managed clusters, all in [kind](https://kind.sigs.k8s.io/). Two small stand-ins cover the ACM-only services:

| ACM service | Lab stand-in |
| :--- | :--- |
| Observability (Thanos behind `rbac-query-proxy`) | `lab-observability`: one Prometheus scraping kube-state-metrics and cAdvisor of each managed cluster through its API server, adding a `cluster` label, with the recording rules in `observability/recording-rules.yml` |
| Search (`search-api` GraphQL) | `lab-search`: `search/server.py` answers the portal's Namespace query in ACM's item shape; the caller's token must be able to list ManagedClusters on the hub |

Needs Docker with about 4 GB free for the lab. Everything secret (kubeconfigs, tokens, CAs) is written to `.state/`, which git ignores.

## Set up

Run from this folder in PowerShell, with [kind](https://github.com/kubernetes-sigs/kind/releases) on the `PATH`. `clusteradm` runs inside the `hub-lab-tools` image because its Windows build cannot load its embedded charts.

```powershell
foreach ($c in 'ocm-hub','cluster1','cluster2') { kind create cluster --name $c }
docker build -t hub-lab-tools tools
New-Item -ItemType Directory -Force .state\kube | Out-Null
foreach ($c in 'ocm-hub','cluster1','cluster2') { kind get kubeconfig --internal --name $c | Set-Content -Encoding ascii ".state\kube\$c.yaml" }

function lab { docker run --rm --network kind -v "$((Resolve-Path .state).Path):/lab/state" -v "$((Resolve-Path scripts).Path):/lab/scripts:ro" hub-lab-tools bash "/lab/scripts/$($args[0])" @($args | Select-Object -Skip 1) }
docker run --rm --network kind -v "$((Resolve-Path .state).Path):/lab/state" -e KUBECONFIG=/lab/state/kube/ocm-hub.yaml hub-lab-tools clusteradm init --wait
lab join-clusters.sh cluster1 cluster2   # register and accept the managed clusters
lab label-clusters.sh                    # ClusterClaims (platform, region) and environment labels
lab portal-reader.sh                     # read-only portal identity -> .state/credentials/ocm-hub-credentials
lab observability.sh cluster1 cluster2   # kube-state-metrics, scrape identities, prometheus.yml
lab workloads.sh                         # namespaces with owner labels, requests and PVCs

docker run -d --name lab-observability --network kind -p 19090:9090 `
  -v "$((Resolve-Path .state\observability).Path):/etc/lab:ro" -v "$((Resolve-Path observability).Path):/etc/lab-rules:ro" `
  prom/prometheus:v3.15.0 --config.file=/etc/lab/prometheus.yml
docker run -d --name lab-search --network kind -p 14010:4010 `
  -v "$((Resolve-Path .state).Path):/lab/state:ro" -v "$((Resolve-Path search).Path):/app:ro" python:3.12-alpine python /app/server.py
```

## Point the portal at it

From the repository root, run the portal against its own database (`openshift_portal_lab`) with the simulator off; the simulator's data is left alone:

```powershell
docker exec ocp-portal-postgres psql -U portal_user -d openshift_portal -c "CREATE DATABASE openshift_portal_lab"
docker compose -f docker-compose.yml -f hub-lab/docker-compose.hub-lab.yml up -d core-service
```

Then, as an ADMIN, register the hub (`POST /api/v1/hubs`):

```json
{
  "name": "ocm-hub-lab",
  "apiUrl": "https://ocm-hub-control-plane:6443",
  "credentialsSecretRef": "ocm-hub-credentials",
  "observabilityUrl": "http://lab-observability:9090",
  "searchUrl": "http://lab-search:4010/searchapi/graphql"
}
```

Create the teams the workloads name (Payments Platform, Digital Channels, Data & AI Analytics with alias `data-science`); `core-banking` is left unknown on purpose. Back to the simulator: `docker compose up -d core-service`.

## Tear down

```powershell
kind delete clusters ocm-hub cluster1 cluster2
docker rm -f lab-observability lab-search
```
