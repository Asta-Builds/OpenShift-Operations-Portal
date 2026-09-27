# clusteradm and kubectl for driving the kind lab. clusteradm runs here because its Windows build cannot load
# its embedded charts.
FROM alpine:3.20

ARG CLUSTERADM_VERSION=v1.3.1
ARG KUBECTL_VERSION=v1.34.1

RUN apk add --no-cache bash curl jq \
 && curl -fsSL "https://github.com/open-cluster-management-io/clusteradm/releases/download/${CLUSTERADM_VERSION}/clusteradm_linux_amd64.tar.gz" \
    | tar -xz -C /usr/local/bin clusteradm \
 && curl -fsSLo /usr/local/bin/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
 && chmod +x /usr/local/bin/kubectl /usr/local/bin/clusteradm

WORKDIR /lab
