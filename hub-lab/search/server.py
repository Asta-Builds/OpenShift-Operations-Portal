"""Stand-in for the ACM Search API (search-api GraphQL) in the kind hub lab.

Answers the query the portal sends, `search(input: [{filters: [{property: "kind", values: ["Namespace"]}]}])`, with
the namespaces of each managed cluster, in the item shape ACM Search returns (labels as "key=value; key=value").
Like ACM Search it authorizes with the caller's hub token: the token must be able to list ManagedClusters on the hub,
and only clusters it can see are returned. Namespaces are read live from each managed cluster with the lab's
read-only identity, standing in for ACM's search-collector. Standard library only.
"""
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STATE = os.environ.get("LAB_STATE", "/lab/state")
HUB_URL = os.environ.get("HUB_URL", "https://ocm-hub-control-plane:6443")
HUB_CA = os.environ.get("HUB_CA", f"{STATE}/credentials/ocm-hub-credentials/ca.crt")
CLUSTERS = [c for c in os.environ.get("CLUSTERS", "cluster1,cluster2").split(",") if c]


def log(message):
    print(message, file=sys.stderr, flush=True)


def get_json(url, token, cafile):
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}", "Accept": "application/json"})
    with urllib.request.urlopen(request, context=ssl.create_default_context(cafile=cafile), timeout=10) as response:
        return json.load(response)


def cluster_server(cluster):
    with open(f"{STATE}/kube/{cluster}.yaml", encoding="utf-8") as kubeconfig:
        return re.search(r"server:\s*(\S+)", kubeconfig.read()).group(1)


def visible_clusters(token):
    """Names of the ManagedClusters the caller may list; raises HTTPError 401/403 otherwise."""
    items = get_json(f"{HUB_URL}/apis/cluster.open-cluster-management.io/v1/managedclusters", token, HUB_CA)["items"]
    return {item["metadata"]["name"] for item in items}


def namespace_items(cluster):
    token_file = f"{STATE}/observability/{cluster}/token"
    with open(token_file, encoding="utf-8") as f:
        token = f.read().strip()
    namespaces = get_json(f"{cluster_server(cluster)}/api/v1/namespaces", token, f"{STATE}/observability/{cluster}/ca.crt")
    for ns in namespaces["items"]:
        meta = ns["metadata"]
        labels = meta.get("labels") or {}
        yield {
            "_uid": f"{cluster}/{meta['uid']}",
            "apiversion": "v1",
            "cluster": cluster,
            "created": meta.get("creationTimestamp"),
            "kind": "Namespace",
            "label": "; ".join(f"{k}={v}" for k, v in sorted(labels.items())),
            "name": meta["name"],
            "status": ns.get("status", {}).get("phase"),
        }


def search(search_input, allowed):
    kinds = set()
    for f in search_input.get("filters") or []:
        if f.get("property") == "kind":
            kinds.update(v.lower() for v in f.get("values") or [])
    items = []
    if not kinds or "namespace" in kinds:
        for cluster in CLUSTERS:
            if cluster not in allowed:
                continue
            try:
                items.extend(namespace_items(cluster))
            except (OSError, urllib.error.URLError) as e:
                # Like a cluster whose search-collector has not reported: it is simply missing from the results
                log(f"cannot index {cluster}: {e}")
    limit = search_input.get("limit", 1000)
    return items if limit is None or limit < 0 else items[:limit]


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path.rstrip("/") != "/searchapi/graphql":
            return self.reply(404, {"message": "not found"})
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return self.reply(401, {"message": "missing bearer token"})
        try:
            allowed = visible_clusters(auth[len("Bearer "):])
        except urllib.error.HTTPError as e:
            return self.reply(e.code if e.code in (401, 403) else 502, {"message": f"hub rejected the token (HTTP {e.code})"})
        except (OSError, urllib.error.URLError) as e:
            return self.reply(502, {"message": f"hub unreachable: {e}"})
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            inputs = (body.get("variables") or {}).get("input") or [{}]
        except (ValueError, AttributeError):
            return self.reply(400, {"errors": [{"message": "invalid GraphQL request"}]})
        results = [{"items": search(i, allowed)} for i in inputs]
        log(f"search: {sum(len(r['items']) for r in results)} items for clusters {sorted(allowed)}")
        return self.reply(200, {"data": {"searchResult": results}})

    def reply(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        log(f"{self.address_string()} {fmt % args}")


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "4010"))
    log(f"ACM Search stand-in on :{port}, hub {HUB_URL}, clusters {CLUSTERS}")
    ThreadingHTTPServer(("", port), Handler).serve_forever()
