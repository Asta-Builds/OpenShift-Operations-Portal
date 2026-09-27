#!/usr/bin/env bash
# Registers the managed kind clusters with the OCM hub and accepts them. Runs in the hub-lab-tools container on the
# "kind" Docker network, with the internal kubeconfigs mounted at /lab/state/kube.
set -euo pipefail

HUB=/lab/state/kube/ocm-hub.yaml
TOKEN=$(KUBECONFIG=$HUB clusteradm get token --output=json | jq -r '."hub-token"')
APISERVER=$(KUBECONFIG=$HUB kubectl config view -o jsonpath='{.clusters[0].cluster.server}')

for cluster in "$@"; do
  if KUBECONFIG=$HUB kubectl get managedcluster "$cluster" >/dev/null 2>&1; then
    echo "$cluster is already registered"
    continue
  fi
  echo "Joining $cluster to $APISERVER"
  KUBECONFIG=/lab/state/kube/$cluster.yaml clusteradm join \
    --hub-token "$TOKEN" --hub-apiserver "$APISERVER" --cluster-name "$cluster" \
    --force-internal-endpoint-lookup --wait
done

KUBECONFIG=$HUB clusteradm accept --clusters "$(IFS=,; echo "$*")" --wait
KUBECONFIG=$HUB kubectl get managedclusters
