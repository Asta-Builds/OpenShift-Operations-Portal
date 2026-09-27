#!/usr/bin/env bash
# Small real workloads in the managed clusters: namespaces carrying the portal's owner and cost-center labels (and
# some without), pods with requests that do a little work, and bound PVCs.
set -euo pipefail

# namespace owner cost-center replicas cpu-request memory-request pvc-size
workload() {
  local ns=$1 owner=$2 cc=$3 replicas=$4 cpu=$5 mem=$6 pvc=$7
  local labels=""
  [ "$owner" != "-" ] && labels+="    openshift.io/owner-team: $owner"$'\n'
  [ "$cc" != "-" ] && labels+="    cost-center: $cc"$'\n'
  kubectl apply -f - >/dev/null <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: $ns
  labels:
$labels    lab: hub-lab
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: data
  namespace: $ns
spec:
  accessModes: ["ReadWriteOnce"]
  resources:
    requests:
      storage: $pvc
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app
  namespace: $ns
spec:
  replicas: $replicas
  selector:
    matchLabels: { app: app }
  template:
    metadata:
      labels: { app: app }
    spec:
      containers:
        - name: app
          image: busybox:1.36
          # Light, steady CPU use so usage metrics have something to show
          command: ["sh", "-c", "while true; do i=0; while [ \$i -lt 20000 ]; do i=\$((i+1)); done; sleep 1; done"]
          resources:
            requests: { cpu: $cpu, memory: $mem }
            limits: { cpu: 500m, memory: 128Mi }
          volumeMounts:
            - { name: data, mountPath: /data }
      volumes:
        - name: data
          persistentVolumeClaim: { claimName: data }
EOF
  echo "  $ns (owner=$owner, cost-center=$cc)"
}

export KUBECONFIG=/lab/state/kube/cluster1.yaml
echo "cluster1:"
workload payments        payments-platform CC-FIN-104 2 250m 64Mi 2Gi
workload storefront      digital-channels  -          2 150m 48Mi 1Gi
workload data-pipeline   data-science      CC-AI-900  1 300m 64Mi 5Gi
workload legacy-batch    core-banking      CC-FIN-001 1 100m 32Mi 1Gi
workload sandbox         -                 -          1 50m  16Mi 1Gi

export KUBECONFIG=/lab/state/kube/cluster2.yaml
echo "cluster2:"
workload storefront-dev  digital-channels  CC-DIG-205 1 100m 32Mi 1Gi
workload payments-dev    Payments-Platform -          1 100m 32Mi 1Gi
workload experiments     -                 -          1 50m  16Mi 1Gi

for c in cluster1 cluster2; do
  export KUBECONFIG=/lab/state/kube/$c.yaml
  for ns in $(kubectl get namespaces -l lab=hub-lab -o jsonpath='{.items[*].metadata.name}'); do
    kubectl -n "$ns" rollout status deployment/app --timeout=300s >/dev/null
  done
done
KUBECONFIG=/lab/state/kube/cluster1.yaml kubectl get pods -A -l app=app --no-headers | awk '{print "cluster1", $1, $4}'
KUBECONFIG=/lab/state/kube/cluster2.yaml kubectl get pods -A -l app=app --no-headers | awk '{print "cluster2", $1, $4}'
