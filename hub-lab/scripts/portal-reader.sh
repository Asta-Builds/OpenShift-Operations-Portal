#!/usr/bin/env bash
# Creates the portal's read-only identity on the hub and writes its token and the hub CA where the portal expects
# a mounted hub Secret: /lab/state/credentials/<secret-name>/{token,ca.crt}.
set -euo pipefail

HUB=/lab/state/kube/ocm-hub.yaml
SECRET_NAME=${1:-ocm-hub-credentials}
OUT=/lab/state/credentials/$SECRET_NAME

KUBECONFIG=$HUB kubectl apply -f - <<'EOF'
apiVersion: v1
kind: Namespace
metadata:
  name: portal-reader
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: portal-reader
  namespace: portal-reader
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: portal-reader
rules:
  - apiGroups: ["cluster.open-cluster-management.io"]
    resources: ["managedclusters"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: portal-reader
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: portal-reader
subjects:
  - kind: ServiceAccount
    name: portal-reader
    namespace: portal-reader
---
# Long-lived token, the way a hub Secret for the portal would be created
apiVersion: v1
kind: Secret
metadata:
  name: portal-reader-token
  namespace: portal-reader
  annotations:
    kubernetes.io/service-account.name: portal-reader
type: kubernetes.io/service-account-token
EOF

for _ in $(seq 1 30); do
  TOKEN=$(KUBECONFIG=$HUB kubectl -n portal-reader get secret portal-reader-token -o jsonpath='{.data.token}' 2>/dev/null || true)
  [ -n "$TOKEN" ] && break
  sleep 1
done

mkdir -p "$OUT"
echo "$TOKEN" | base64 -d > "$OUT/token"
KUBECONFIG=$HUB kubectl -n portal-reader get secret portal-reader-token -o jsonpath='{.data.ca\.crt}' | base64 -d > "$OUT/ca.crt"
chmod 0644 "$OUT/token" "$OUT/ca.crt"

# The token may list managed clusters and nothing else
KUBECONFIG=$HUB kubectl auth can-i list managedclusters --as=system:serviceaccount:portal-reader:portal-reader
KUBECONFIG=$HUB kubectl auth can-i list secrets -A --as=system:serviceaccount:portal-reader:portal-reader || true
echo "Wrote $OUT"
