# Conjur OSS on OpenShift Container Platform — Deployment Guide

## Prerequisites

- OpenShift 4.12+ cluster with `oc` CLI authenticated
- `cluster-admin` role (for SCC binding in Step 3)
- Access to push images to the OpenShift internal registry or an external registry

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  OpenShift Routes (TLS Edge)                                    │
│  conjur-server-conjur-system.apps.ocp.example.com               │
│  conjur-rest-apps.apps.ocp.example.com                          │
│  conjur-admin-apps.apps.ocp.example.com (Web UI)                │
└────────────┬──────────────────┬──────────────────┬──────────────┘
             │                  │                  │
     ┌───────▼───────┐  ┌──────▼──────┐  ┌───────▼───────┐
     │ Conjur Server  │  │ Conjur REST │  │  Conjur Web   │
     │ (conjur-system)│  │   (apps)    │  │    (apps)     │
     │   Port 80      │  │  Port 9080  │  │  Port 9080    │
     └───────┬────────┘  └──────┬──────┘  └───────────────┘
             │                  │
     ┌───────▼────────┐        │  (REST API calls Conjur
     │  PostgreSQL     │        │   internally via ClusterIP)
     │ (conjur-system) │        │
     │   Port 5432     │        │
     └─────────────────┘        │
                                │
     ┌──────────────────────────▼──────────────────────────┐
     │  App Pods (apps namespace)                           │
     │  - conjur-microprofile-lib (shared library)          │
     │  - Per-app K8s Secret (conjur-{appname})             │
     │  - CONJUR_SECRETS env var → auto-injected creds      │
     └─────────────────────────────────────────────────────┘
```

## SCC Strategy

| Component        | Image                    | SCC Required           | Why                                |
|------------------|--------------------------|------------------------|------------------------------------|
| PostgreSQL       | `postgres:15`            | `anyuid`               | Runs as UID 999                    |
| Conjur Server    | `cyberark/conjur`        | `anyuid`               | Runs internal processes as root    |
| Conjur REST API  | Open Liberty (custom)    | `restricted` (default) | Liberty runs as UID 1001           |
| Conjur Web UI    | Open Liberty (custom)    | `restricted` (default) | Liberty runs as UID 1001           |

## Deployment Steps

### Step 1: Create namespaces

```bash
oc apply -f 00-namespace.yaml
```

### Step 2: Create ServiceAccounts

```bash
oc apply -f 02-serviceaccounts.yaml
```

### Step 3: Grant anyuid SCC to Conjur server + PostgreSQL (requires cluster-admin)

Both `postgres:15` (UID 999) and `cyberark/conjur` (root) need the `anyuid` SCC.

Choose **one** of these methods — they are equivalent:

**Option A: Imperative (recommended)**
```bash
oc adm policy add-scc-to-user anyuid -z conjur-server-sa -n conjur-system
oc adm policy add-scc-to-user anyuid -z conjur-postgres-sa -n conjur-system
```

**Option B: Declarative**
```bash
oc apply -f 01-scc.yaml
```

Verify the bindings:
```bash
oc get clusterrolebinding conjur-server-anyuid conjur-postgres-anyuid
```

### Step 4: Apply RBAC (Secret management roles)

```bash
oc apply -f 03-rbac.yaml
```

### Step 5: Deploy PostgreSQL

```bash
oc apply -f 04-postgres.yaml
oc wait --for=condition=ready pod -l app=conjur-postgres -n conjur-system --timeout=120s
```

> **Note:** PostgreSQL uses `postgres:15` with `anyuid` SCC (UID 999).
> `PGDATA` is set to a subdirectory (`/var/lib/postgresql/data/pgdata`) to
> avoid `chmod` errors on the PVC mount root.

### Step 6: Deploy Conjur server

```bash
oc apply -f 05-conjur-server.yaml
oc wait --for=condition=ready pod -l app=conjur-server -n conjur-system --timeout=180s
```

### Step 7: Initialize Conjur account

```bash
CONJUR_POD=$(oc get pod -l app=conjur-server -n conjur-system -o jsonpath='{.items[0].metadata.name}')

# Create the account — SAVE THE ADMIN API KEY!
oc exec $CONJUR_POD -n conjur-system -- conjurctl account create myConjurAccount

# If account already exists, retrieve the key:
# oc exec $CONJUR_POD -n conjur-system -- conjurctl role retrieve-key myConjurAccount:user:admin
```

### Step 8: Push images to OpenShift internal registry

```bash
# Expose the internal registry (if not already)
oc patch configs.imageregistry.operator.openshift.io/cluster --type merge \
  -p '{"spec":{"defaultRoute":true}}'

# Login to OpenShift registry
REGISTRY=$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}')
docker login $REGISTRY -u $(oc whoami) -p $(oc whoami -t)

# Tag and push images
docker tag localhost:30500/conjur-microprofile-rest:latest $REGISTRY/apps/conjur-microprofile-rest:latest
docker push $REGISTRY/apps/conjur-microprofile-rest:latest

docker tag localhost:30500/conjur-microprofile-web:latest $REGISTRY/apps/conjur-microprofile-web:latest
docker push $REGISTRY/apps/conjur-microprofile-web:latest
```

### Step 9: Update the conjur-app-identity Secret with real API keys

```bash
oc patch secret conjur-app-identity -n apps --type merge -p "{
  \"stringData\": {
    \"CONJUR_ADMIN_API_KEY\": \"<admin-api-key-from-step-7>\",
    \"CONJUR_AUTHN_API_KEY\": \"<host-api-key-from-policy-load>\"
  }
}"
```

### Step 10: Deploy admin apps + network policies + Routes

```bash
oc apply -f 06-conjur-admin-apps.yaml
oc apply -f 07-networkpolicy.yaml
oc apply -f 08-routes.yaml

oc wait --for=condition=ready pod -l app=conjur-rest -n apps --timeout=180s
oc wait --for=condition=ready pod -l app=conjur-web -n apps --timeout=180s
```

### Step 11: Verify Routes

```bash
oc get routes -n conjur-system
oc get routes -n apps

# Test access
CONJUR_URL=$(oc get route conjur-server -n conjur-system -o jsonpath='{.spec.host}')
curl -k https://$CONJUR_URL/

REST_URL=$(oc get route conjur-rest -n apps -o jsonpath='{.spec.host}')
curl -k https://$REST_URL/api/status

WEB_URL=$(oc get route conjur-web -n apps -o jsonpath='{.spec.host}')
echo "Web UI: https://$WEB_URL"
```

## Onboarding Apps (1000+ microservices)

### Using the Bulk Onboard API

```bash
REST_URL=$(oc get route conjur-rest -n apps -o jsonpath='{.spec.host}')

curl -k -X POST https://$REST_URL/api/onboard/full \
  -H "Content-Type: application/json" \
  -d '{
    "apps": [
      {"name": "order-service", "namespace": "apps", "databases": ["orderdb"]},
      {"name": "inventory-service", "namespace": "apps", "databases": ["inventorydb"]}
    ]
  }'
```

### Using the OpenShift Template

```bash
oc process -f 09-app-template.yaml \
  -p APP_NAME=order-service \
  -p IMAGE=image-registry.openshift-image-registry.svc:5000/apps/order-service:latest \
  -p DATABASES=orderdb \
| oc apply -f -
```

## Troubleshooting

### SCC-related pod failures

```bash
# Check which SCC a running pod is using
oc get pod <pod-name> -n <namespace> -o jsonpath='{.metadata.annotations.openshift\.io/scc}'

# Check which SCCs a ServiceAccount can use
oc adm policy who-can use scc anyuid -n conjur-system

# Verify the anyuid binding exists
oc get clusterrolebinding conjur-server-anyuid -o yaml
```

### PostgreSQL won't start

```bash
# Check pod events
oc describe pod -l app=conjur-postgres -n conjur-system

# Verify anyuid SCC is bound (should show "anyuid")
oc get pod -l app=conjur-postgres -n conjur-system \
  -o jsonpath='{.items[0].metadata.annotations.openshift\.io/scc}'

# If still "restricted", re-apply the SCC binding:
oc adm policy add-scc-to-user anyuid -z conjur-postgres-sa -n conjur-system
oc rollout restart deployment/conjur-postgres -n conjur-system

# Check PVC is bound
oc get pvc conjur-postgres-data -n conjur-system
```

### Conjur server won't start

```bash
# Check pod events — look for SCC errors
oc describe pod -l app=conjur-server -n conjur-system

# Verify anyuid SCC is bound (should show "anyuid")
oc get pod -l app=conjur-server -n conjur-system \
  -o jsonpath='{.items[0].metadata.annotations.openshift\.io/scc}'

# If still "restricted", re-apply the SCC binding:
oc adm policy add-scc-to-user anyuid -z conjur-server-sa -n conjur-system
oc rollout restart deployment/conjur-server -n conjur-system
```

## Key Differences from Docker Desktop K8s

| Feature             | Docker Desktop K8s          | OpenShift                              |
|---------------------|-----------------------------|----------------------------------------|
| External access     | NodePort (30080, 30086...)  | Routes with TLS (auto-hostname)        |
| Security            | No restrictions             | SCCs + NetworkPolicies                 |
| Registry            | localhost:30500              | Internal OpenShift registry             |
| Service accounts    | Optional                    | Required (SCC bindings)                |
| TLS                 | None                        | Edge termination on Routes             |
| PostgreSQL image    | `postgres:15` (UID 999)     | `postgres:15` + anyuid SCC             |
| Templates           | N/A                         | OpenShift Templates for app onboarding |
| Pod security        | Unrestricted                | restricted-v2 SCC (default)            |
