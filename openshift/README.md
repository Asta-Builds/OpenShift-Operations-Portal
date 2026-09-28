# Deploying on OpenShift

Everything needed to run the portal on OpenShift, roll the node agent out to managed clusters with ACM, and run the
unit tests in OpenShift Pipelines. Every pod runs under the default `restricted-v2` SCC: an arbitrary UID in group 0,
no capabilities, `RuntimeDefault` seccomp, and a read-only root filesystem for the Java services.

```
openshift/
├── base/                    # Core service and UI: Deployments, Services, Route, PodDisruptionBudgets, NetworkPolicies
│   └── config.env           # Settings shared by every environment (ConfigMap openshift-operations-portal-config)
├── components/
│   ├── postgresql/          # PostgreSQL 15 StatefulSet (Red Hat image); leave out for an external database
│   └── rabbitmq/            # RabbitMQ StatefulSet for the report and email queues; optional
├── overlays/example/        # One environment: copy it per cluster and fill in its settings
├── acm/                     # Applied on ACM hubs: the portal's read-only hub identity, the node agent Policy
├── pipeline/                # Tekton v1 pipeline: backend and node agent unit tests, UI production build
├── schemas/                 # JSON schemas of Route and the ACM kinds, for kubeconform
└── validate.sh              # Static checks run by CI (kustomize build + kubeconform + Tekton consistency)
```

## Topology

```
  browsers        node agent in each managed cluster (installed by the ACM Policy): POST /api/v1/node-reports
      \                 /
       Route (edge TLS)
             |
  openshift-operations-portal-ui       nginx: the Angular app, /api/v1 proxied to the core service
             |
  openshift-operations-portal-core     Spring Boot, profile prod
      |           |          |        |           |
  PostgreSQL   RabbitMQ     SMTP   Keycloak    ACM hubs: API, Observability, Search (one token per hub)
```

NetworkPolicies deny all ingress to the namespace except the router to the UI, the UI to the core service, and the core
service to PostgreSQL and RabbitMQ. The core service's actuator endpoints are not reachable through the Route.

## 1. Build the images

Build from the repository root and push to a registry the cluster can pull from. In an air-gapped installation, mirror
the base images first (`node:20-alpine`, `maven:3.9-eclipse-temurin-17-alpine`, `eclipse-temurin:17-jre-alpine`,
`nginxinc/nginx-unprivileged:1.27-alpine`) and point the `FROM` lines or an `ImageDigestMirrorSet` at the mirror.

```bash
REGISTRY=quay.example.com/openshift-portal
TAG=1.0.0
podman build -t $REGISTRY/core-service:$TAG core-service
podman build -t $REGISTRY/ui:$TAG frontend
podman build -t $REGISTRY/node-agent:$TAG node-agent
for image in core-service ui node-agent; do podman push $REGISTRY/$image:$TAG; done
```

The components also pull `registry.redhat.io/rhel9/postgresql-15` (needs the cluster's Red Hat pull secret) and
`docker.io/library/rabbitmq:3.13-alpine`.

## 2. Prepare Keycloak

The portal needs a realm with the clients and roles of the sandbox realm, [keycloak/openshift-portal-realm.json](../keycloak/openshift-portal-realm.json).
Import it into the Red Hat build of Keycloak as a starting point, then change what is sandbox-only:

| Client | Type | Change for the cluster |
| :--- | :--- | :--- |
| `portal-ui` | Public, authorization code + PKCE | Valid redirect URI `https://<route host>/*`, web origin `https://<route host>`; remove the `localhost` entries |
| `portal-api` | Audience only (no sign-in) | Nothing: `portal-ui`, `portal-cli` and `portal-node-agent` put it in their tokens' `aud` with an audience mapper |
| `portal-node-agent` | Confidential, service account only | Regenerate the client secret; its service account holds the `portal-node-agent` realm role |
| `portal-cli` | Public, password grant | Delete it unless scripts need it |

Realm roles `portal-admin`, `portal-operator` and `portal-viewer` map to the portal's ADMIN, OPERATOR and VIEWER roles.
Replace the sandbox LDAP federation with your directory and map its groups to those roles.

The core service fetches the realm's signing keys from `KEYCLOAK_ISSUER_URI`, so it must reach that URL from the pod. The
issuer must be exactly the URL browsers use: tokens with any other issuer are rejected.

## 3. Create hub credentials

The portal reads each ACM hub with a token that can list `ManagedClusters` and nothing else. On every hub:

```bash
oc apply -f openshift/acm/hub-reader.yaml
oc -n openshift-portal-reader get secret openshift-portal-reader-token -o jsonpath='{.data.token}' | base64 -d > token
oc -n openshift-portal-reader get secret openshift-portal-reader-token -o jsonpath='{.data.ca\.crt}' | base64 -d > ca.crt
# Observability and Search are routes, served with the ingress certificate: trust its CA too
oc -n openshift-config-managed get configmap default-ingress-cert -o jsonpath='{.data.ca-bundle\.crt}' >> ca.crt
```

The portal trusts exactly the certificates in `ca.crt` for every endpoint of that hub, so it must hold the CAs of the
API server and of the routes. The token Secret's CA covers the API server unless it has a custom certificate; if both
certificates come from a publicly trusted CA, leave `ca.crt` out and the JVM's trust store is used.

ACM Observability and Search only return what the caller may see on the hub. If namespace metrics or labels come back
empty, also give `openshift-portal-reader` read access to the managed clusters' namespaces on the hub (for example
the `view` ClusterRole bound in each).

## 4. Deploy the portal

Copy the example overlay for your environment:

```bash
cp -r openshift/overlays/example openshift/overlays/prod
```

and edit it:

* `kustomization.yaml`: the image names and tags from step 1; leave out `components/postgresql` for an external
  database or `components/rabbitmq` to run without RabbitMQ (see [Settings](#settings)).
* `config.env`: the Keycloak issuer, the SMTP server and the portal URL used in emails.
* `route-host.yaml`: the Route host, or delete the patch to take the cluster's default `<route>-<namespace>.apps...` name.
* `hub-credentials.yaml`: one projected Secret source per hub. The core service finds each hub's token at
  `/var/run/secrets/acm-hubs/<secret name>/token`. For a hub without `ca.crt`, remove that item too: a listed key
  missing from the Secret stops the pod from starting.

Then create the namespace and Secrets, and apply the overlay:

```bash
oc new-project openshift-operations-portal

cp openshift/overlays/prod/secrets.env.example openshift/overlays/prod/secrets.env   # ignored by Git; fill in
oc create secret generic openshift-operations-portal-secrets --from-env-file=openshift/overlays/prod/secrets.env

# One per hub, from the token and ca.crt of step 3 (leave out ca.crt for a publicly trusted CA)
oc create secret generic hub-east-credentials --from-file=token --from-file=ca.crt

oc apply -k openshift/overlays/prod
oc rollout status deployment/openshift-operations-portal-core
oc get route openshift-operations-portal
```

The core service creates its schema with Flyway on first start; its startup probe allows three minutes for that.

Finally register each hub as an ADMIN, with the Secret name as `credentialsSecretRef` (see
[Connecting a real ACM hub](../README.md#connecting-a-real-acm-hub)):

```bash
curl -X POST https://<route host>/api/v1/hubs -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"hub-east","apiUrl":"https://api.hub-east.example.com:6443","credentialsSecretRef":"hub-east-credentials"}'
```

### Settings

Settings are environment variables. Non-secret ones come from the ConfigMap generated from `base/config.env` and the
overlay's `config.env` (the overlay's values win), secret ones from `openshift-operations-portal-secrets`. Any Spring
property can be set the same way in relaxed-binding form, e.g. `openshift.portal.licensing.licensed-cap-cores` as
`OPENSHIFT_PORTAL_LICENSING_LICENSEDCAPCORES`. Kustomize adds a content hash to the ConfigMap name, so a changed
setting rolls the Deployment on the next `oc apply -k`; a changed Secret needs `oc rollout restart`.

| Variable | Set in | Meaning |
| :--- | :--- | :--- |
| `KEYCLOAK_ISSUER_URI` | overlay | Realm URL as browsers reach it |
| `KEYCLOAK_AUDIENCE` | base | Required `aud` of API tokens (`portal-api`) |
| `SPRING_DATASOURCE_URL` | base | Points at the PostgreSQL component; change it for an external database |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | Secret | Database user; the PostgreSQL component creates it |
| `OPENSHIFT_PORTAL_RABBITMQ_ENABLED` | component | `true` with the RabbitMQ component; without it reports and emails are processed in-process |
| `SPRING_RABBITMQ_HOST` / `PORT` | component | Set by the RabbitMQ component; set them yourself for an external broker |
| `SPRING_RABBITMQ_USERNAME` / `PASSWORD` | Secret | Broker user; the RabbitMQ component creates it |
| `SPRING_MAIL_HOST` / `PORT` and `SPRING_MAIL_PROPERTIES_MAIL_SMTP_*` | overlay | SMTP relay for report schedules and alerts |
| `SPRING_MAIL_USERNAME` / `PASSWORD` | Secret | SMTP login; empty for a relay without authentication |
| `OPENSHIFT_PORTAL_NOTIFICATIONS_*` | overlay | Sender, alert recipients and the portal URL in emails |
| `OPENSHIFT_PORTAL_COLLECTOR_CRON` | base | Hub collection schedule (every 15 minutes) |
| `OPENSHIFT_PORTAL_SECURITY_ENABLED`, `OPENSHIFT_PORTAL_SIMULATOR_ENABLED` | base | `true` and `false`; do not change in a cluster |

The UI image has one setting, `PORTAL_API_URL`, the core service's in-cluster URL (set in `base/ui.yaml`).

## 5. Roll the node agent out with ACM

The node agent reports each cluster's nodes, which ACM does not provide; without it a cluster shows no nodes or
license cores. On the hub that manages the clusters:

```bash
# Fill in the portal URL, token endpoint and image first; create the Secret from the command line
oc apply -f openshift/acm/node-agent-settings.yaml
oc -n openshift-portal-policies create secret generic node-agent-credentials \
  --from-literal=client-id=portal-node-agent --from-literal=client-secret=<portal-node-agent client secret> \
  --dry-run=client -o yaml | oc apply -f -
oc apply -f openshift/acm/node-agent-policy.yaml
oc -n openshift-portal-policies get policy openshift-portal-node-agent
```

The Policy installs, in every managed cluster of the `global` cluster set (including the hub itself as
`local-cluster`), the same objects as the single-cluster [node-agent/deploy/node-agent.yaml](../node-agent/deploy/node-agent.yaml),
with `CLUSTER_NAME` set to the cluster's ManagedCluster name, which is how the portal matches reports to clusters. It
keeps them in place (`enforce`) and removes them if the Policy is deleted. To stage the rollout, narrow the
Placement with a label selector (example in the file).

The hub resolves the templates again when `node-agent-settings` or `node-agent-credentials` change; to force it, set
the `policy.open-cluster-management.io/trigger-update` annotation on the Policy to a new value. A new `IMAGE` rolls
the agents out. The other values are environment variables, so running agents pick them up at their next restart
(`oc -n openshift-portal-node-agent rollout restart deployment/node-agent` in a managed cluster).

Managed clusters send reports to the portal's Route, so they must reach it and trust its certificate. For a private CA,
see `PORTAL_SSL_BUNDLE` in [node-agent/README.md](../node-agent/README.md).

## 6. Unit tests in OpenShift Pipelines

The pipeline clones the repository, runs the core service and node agent unit tests with JaCoCo coverage, and builds
the UI for production. Its tasks are inline, so it needs no ClusterTask (removed in OpenShift Pipelines 1.17) and
no catalog task. The UI unit tests need a browser and run in GitHub Actions only.

```bash
oc apply -f openshift/pipeline/unit-test-pipeline.yaml
oc create -f openshift/pipeline/pipelinerun-sample.yaml
tkn pipelinerun logs --last -f
```

For an air-gapped cluster, set the `git-image`, `maven-image` and `node-image` parameters to mirrored images and
`maven-mirror-url` to a Maven repository mirror (e.g. a Nexus group). Bind the optional `maven-cache` workspace to a
PersistentVolumeClaim to keep downloaded dependencies between runs.

## Validating changes

```bash
./openshift/validate.sh
```

It builds every overlay and checks the result, the ACM manifests, the objects the node agent Policy creates, and the
node agent's single-cluster manifest against the Kubernetes 1.29 (OpenShift 4.16) schemas in strict mode, so unknown
or misplaced fields fail. It also checks that the Tekton pipeline's parameters, workspaces and task order are
consistent. It needs `kustomize`, `kubeconform`, `python3` and PyYAML. CI runs it on every change, and also builds the
three images and starts each as an arbitrary UID in group 0, the Java services with a read-only root filesystem.

## Operational notes

* **Database backups.** The PostgreSQL component is a single instance on one PersistentVolumeClaim. Back it up (e.g.
  OADP or `pg_dump` from a CronJob), or use a managed or operator-run PostgreSQL for high availability.
* **Scaling.** The core service and UI run two replicas each. Scheduled jobs (hub collection, report schedules,
  inventory import) take a ShedLock lock in PostgreSQL, so each runs once per cycle whatever the replica count.
* **Long requests.** The Route allows 120 seconds per request for large exports; the UI's proxy allows the same.
* **Rotating hub tokens.** Delete and recreate `openshift-portal-reader-token` on the hub, update the hub's Secret in
  the portal namespace, then `oc rollout restart deployment/openshift-operations-portal-core`.
