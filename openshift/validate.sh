#!/usr/bin/env bash
# Static checks of the OpenShift manifests, run by CI and before a change:
#   - every overlay builds and matches the Kubernetes, OpenShift Route and ACM schemas (strict: unknown fields fail)
#   - the ACM manifests match, and so do the objects the node agent Policy creates in managed clusters
#   - the node agent's single-cluster manifest matches
#   - the Tekton pipeline's parameters, workspaces and task order are consistent
# Needs kustomize, kubeconform, python3 and PyYAML. kubeconform downloads the Kubernetes schemas; the Route and ACM
# schemas are in schemas/ (generated from their CRDs), so those checks also work offline.
set -euo pipefail
cd "$(dirname "$0")"

KUSTOMIZE=${KUSTOMIZE:-kustomize}
KUBECONFORM=${KUBECONFORM:-kubeconform}
# OpenShift 4.16
KUBERNETES_VERSION=${KUBERNETES_VERSION:-1.29.0}

conform() {
  "$KUBECONFORM" -strict -summary -kubernetes-version "$KUBERNETES_VERSION" \
    -schema-location default \
    -schema-location 'schemas/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' "$@"
}

for overlay in overlays/*/; do
  echo "== overlay ${overlay}"
  "$KUSTOMIZE" build "$overlay" | conform -
done

echo "== ACM manifests"
conform acm/*.yaml

echo "== objects the node agent Policy creates"
python3 validate.py policy-objects acm/node-agent-policy.yaml | conform -

echo "== node agent, single cluster"
conform ../node-agent/deploy/node-agent.yaml

echo "== Tekton pipeline"
python3 validate.py tekton pipeline/*.yaml
