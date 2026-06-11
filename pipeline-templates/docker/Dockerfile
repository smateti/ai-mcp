# =============================================================================
# Custom GitLab Runner Build Image
# =============================================================================
# Contains all tools needed by the service pipeline:
#   Java 21, Maven, Python 3, pip, jq, curl, oc, helm, buildah,
#   and the ca-certificates package for runtime cert injection.
#
# Based on UBI 9 for RHEL/OpenShift compatibility.
#
# SSL NOTE: If building behind a corporate proxy / firewall with TLS
# inspection, pass your CA bundle at build time:
#   docker build --build-arg EXTRA_CA_BUNDLE=./my-corp-ca.pem ...
# =============================================================================

FROM registry.access.redhat.com/ubi9/ubi:latest

LABEL maintainer="platform-engineering@example.com"
LABEL description="GitLab CI runner image for service pipelines"

# ── Build-time CA injection ──────────────────────────────────────────────────
# Copies an optional extra CA bundle into the OS trust store BEFORE any
# network-dependent steps (dnf, curl, pip) so TLS verification succeeds
# when building behind a corporate proxy that performs TLS inspection.
#
# Usage:  docker build --build-arg EXTRA_CA_BUNDLE=./corporate-ca.pem .
# If not provided, this step is a no-op.
ARG EXTRA_CA_BUNDLE=""
COPY ${EXTRA_CA_BUNDLE:-.dockerignore} /tmp/maybe-ca-bundle
RUN if [ -n "${EXTRA_CA_BUNDLE}" ]; then \
      cp /tmp/maybe-ca-bundle /etc/pki/ca-trust/source/anchors/extra-ca.pem \
      && update-ca-trust extract \
      && echo "Build-time CA bundle injected."; \
    else \
      echo "No EXTRA_CA_BUNDLE provided; skipping build-time cert injection."; \
    fi \
    && rm -f /tmp/maybe-ca-bundle

# ── Environment variables ────────────────────────────────────────────────────
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
    MAVEN_VERSION=3.9.6 \
    HELM_VERSION=3.14.2 \
    OC_VERSION=4.14.0 \
    PATH="/opt/maven/bin:${PATH}" \
    # Ensure Python / pip / requests honour the OS trust store
    REQUESTS_CA_BUNDLE=/etc/pki/tls/certs/ca-bundle.crt \
    SSL_CERT_FILE=/etc/pki/tls/certs/ca-bundle.crt \
    CURL_CA_BUNDLE=/etc/pki/tls/certs/ca-bundle.crt

# ── System packages ─────────────────────────────────────────────────────────
# ca-certificates: the OS trust store, required for update-ca-trust
# java-21-openjdk-devel: JDK 21 for Maven builds + keytool for cert import
# python3 + pip: for orchestration scripts
# jq: for JSON parsing in shell scripts
# buildah: rootless container image builds (no Docker-in-Docker needed)
# fuse-overlayfs: storage driver for rootless buildah on OpenShift
RUN dnf install -y --setopt=tsflags=nodocs \
      ca-certificates \
      java-21-openjdk-devel \
      python3 \
      python3-pip \
      jq \
      curl \
      unzip \
      git \
      buildah \
      fuse-overlayfs \
    && dnf clean all \
    && rm -rf /var/cache/dnf

# ── Python dependencies ─────────────────────────────────────────────────────
RUN pip3 install --no-cache-dir pyyaml requests

# ── Maven ────────────────────────────────────────────────────────────────────
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
      | tar -xz -C /opt \
    && mv "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven \
    && mvn --version

# ── Helm ─────────────────────────────────────────────────────────────────────
RUN curl -fsSL "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz" \
      | tar -xz -C /tmp \
    && mv /tmp/linux-amd64/helm /usr/local/bin/helm \
    && rm -rf /tmp/linux-amd64 \
    && helm version

# ── OpenShift CLI (oc) ───────────────────────────────────────────────────────
RUN curl -fsSL "https://mirror.openshift.com/pub/openshift-v4/clients/ocp/${OC_VERSION}/openshift-client-linux.tar.gz" \
      | tar -xz -C /usr/local/bin oc kubectl \
    && oc version --client

# ── Import build-time CA into the JVM truststore ────────────────────────────
# If an extra CA was injected above, also add it to the JDK's cacerts so
# Maven / any Java process trusts internal HTTPS endpoints during the build.
RUN if [ -f /etc/pki/ca-trust/source/anchors/extra-ca.pem ]; then \
      keytool -importcert -noprompt \
        -alias build-extra-ca \
        -file /etc/pki/ca-trust/source/anchors/extra-ca.pem \
        -cacerts \
        -storepass changeit \
      && echo "Extra CA imported into JVM truststore."; \
    fi

# ── Buildah configuration for rootless / OpenShift ───────────────────────────
# Use vfs storage driver when fuse is not available (common in unprivileged pods)
RUN echo '[storage]' > /etc/containers/storage.conf \
    && echo 'driver = "vfs"' >> /etc/containers/storage.conf

# ── Create non-root user for rootless buildah ────────────────────────────────
RUN useradd -m -u 1001 runner
USER 1001

# Verify tools are accessible
RUN java -version && mvn --version && python3 --version && helm version && oc version --client && buildah --version

WORKDIR /workspace
