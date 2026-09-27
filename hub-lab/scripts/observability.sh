#!/usr/bin/env bash
# Stand-in for ACM Observability: kube-state-metrics in each managed cluster, scraped (with cAdvisor) through the
# cluster's API server by one Prometheus on the "kind" network that labels every series with its cluster, as ACM's
# metrics collector does. Writes the scrape identities and prometheus.yml under /lab/state/observability.
set -euo pipefail

KSM_VERSION=v2.20.0
KSM_BASE=https://raw.githubusercontent.com/kubernetes/kube-state-metrics/$KSM_VERSION/examples/standard
OUT=/lab/state/observability
mkdir -p "$OUT"

scrape_jobs=""
for cluster in "$@"; do
  export KUBECONFIG=/lab/state/kube/$cluster.yaml
  for file in service-account cluster-role cluster-role-binding deployment service; do
    kubectl apply -f "$KSM_BASE/$file.yaml" >/dev/null
  done
  # Several kind clusters share one Docker VM; the upstream 5s probes restart kube-state-metrics under load,
  # which leaves gaps in its series
  kubectl -n kube-system patch deployment kube-state-metrics --type=json -p='[
    {"op":"replace","path":"/spec/template/spec/containers/0/livenessProbe/timeoutSeconds","value":15},
    {"op":"replace","path":"/spec/template/spec/containers/0/livenessProbe/failureThreshold","value":6},
    {"op":"replace","path":"/spec/template/spec/containers/0/readinessProbe/timeoutSeconds","value":15},
    {"op":"add","path":"/spec/template/spec/containers/0/resources","value":{"requests":{"cpu":"100m","memory":"64Mi"}}}
  ]' >/dev/null

  # Read-only scrape identity: kube-state-metrics through the service proxy, cAdvisor through the node proxy
  kubectl apply -f - >/dev/null <<'EOF'
apiVersion: v1
kind: Namespace
metadata:
  name: lab-observability
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: scraper
  namespace: lab-observability
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: lab-observability-scraper
rules:
  - apiGroups: [""]
    resources: ["nodes"]
    verbs: ["get", "list"]
  - apiGroups: [""]
    resources: ["nodes/proxy", "nodes/metrics"]
    verbs: ["get"]
  - apiGroups: [""]
    resources: ["services/proxy"]
    resourceNames: ["kube-state-metrics:http-metrics"]
    verbs: ["get"]
  # For the Search stand-in, which indexes namespaces like ACM's search-collector
  - apiGroups: [""]
    resources: ["namespaces"]
    verbs: ["list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: lab-observability-scraper
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: lab-observability-scraper
subjects:
  - kind: ServiceAccount
    name: scraper
    namespace: lab-observability
---
apiVersion: v1
kind: Secret
metadata:
  name: scraper-token
  namespace: lab-observability
  annotations:
    kubernetes.io/service-account.name: scraper
type: kubernetes.io/service-account-token
EOF
  for _ in $(seq 1 30); do
    token=$(kubectl -n lab-observability get secret scraper-token -o jsonpath='{.data.token}' 2>/dev/null || true)
    [ -n "$token" ] && break
    sleep 1
  done
  mkdir -p "$OUT/$cluster"
  echo "$token" | base64 -d > "$OUT/$cluster/token"
  kubectl -n lab-observability get secret scraper-token -o jsonpath='{.data.ca\.crt}' | base64 -d > "$OUT/$cluster/ca.crt"
  chmod 0644 "$OUT/$cluster/token" "$OUT/$cluster/ca.crt"
  kubectl -n kube-system rollout status deployment/kube-state-metrics --timeout=180s >/dev/null

  server=$(kubectl config view -o jsonpath='{.clusters[0].cluster.server}')
  host=${server#https://}
  node=$(kubectl get nodes -o jsonpath='{.items[0].metadata.name}')
  scrape_jobs+="
  - job_name: kube-state-metrics-$cluster
    scheme: https
    metrics_path: /api/v1/namespaces/kube-system/services/kube-state-metrics:http-metrics/proxy/metrics
    authorization: { credentials_file: /etc/lab/$cluster/token }
    tls_config: { ca_file: /etc/lab/$cluster/ca.crt }
    honor_labels: true
    static_configs:
      - targets: ['$host']
        labels: { cluster: $cluster }
  - job_name: cadvisor-$cluster
    scheme: https
    metrics_path: /api/v1/nodes/$node/proxy/metrics/cadvisor
    authorization: { credentials_file: /etc/lab/$cluster/token }
    tls_config: { ca_file: /etc/lab/$cluster/ca.crt }
    static_configs:
      - targets: ['$host']
        labels: { cluster: $cluster }
    metric_relabel_configs:
      # Keep what the portal's queries use
      - source_labels: [__name__]
        regex: container_cpu_usage_seconds_total|container_memory_working_set_bytes
        action: keep"
  echo "$cluster: kube-state-metrics $KSM_VERSION running, scrape identity written"
done

cat > "$OUT/prometheus.yml" <<EOF
global:
  scrape_interval: 30s
  evaluation_interval: 30s
rule_files:
  - /etc/lab-rules/recording-rules.yml
scrape_configs:$scrape_jobs
EOF
echo "Wrote $OUT/prometheus.yml"
