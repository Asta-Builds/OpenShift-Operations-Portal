#!/usr/bin/env bash
# Describes the managed clusters the way ACM would: ClusterClaims on each managed cluster (reported to the hub by
# the registration agent) and an environment label on its ManagedCluster. kind clusters are not OpenShift, so no
# version.openshift.io claim is set.
set -euo pipefail

HUB=/lab/state/kube/ocm-hub.yaml

describe() {
  local cluster=$1 environment=$2 region=$3
  KUBECONFIG=/lab/state/kube/$cluster.yaml kubectl apply -f - <<EOF
apiVersion: cluster.open-cluster-management.io/v1alpha1
kind: ClusterClaim
metadata:
  name: platform.open-cluster-management.io
spec:
  value: Other
---
apiVersion: cluster.open-cluster-management.io/v1alpha1
kind: ClusterClaim
metadata:
  name: region.open-cluster-management.io
spec:
  value: $region
EOF
  KUBECONFIG=$HUB kubectl label managedcluster "$cluster" "environment=$environment" --overwrite
}

describe cluster1 production lab-east
describe cluster2 development lab-west
