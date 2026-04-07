# Conjur JWT-Based Kubernetes Authentication

## Setup Guide for `nimbus/dev/product1/apps`

> Based on [CyberArk Conjur OSS Documentation](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/k8s-ocp/k8s-jwt-authn.htm)
> and [joetanx/conjur-k8s Reference Implementation](https://github.com/joetanx/conjur-k8s)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Architecture](#3-architecture)
4. [Step 1 - Retrieve Kubernetes Cluster Information](#step-1---retrieve-kubernetes-cluster-information)
5. [Step 2 - RBAC for JWKS Access](#step-2---rbac-for-jwks-access)
6. [Step 3 - Mount K8s CA into Conjur Pod](#step-3---mount-k8s-ca-into-conjur-pod)
7. [Step 4 - Create JWT Authenticator Policy](#step-4---create-jwt-authenticator-policy)
8. [Step 5 - Set JWT Authenticator Variables](#step-5---set-jwt-authenticator-variables)
9. [Step 6 - Enable the Authenticator](#step-6---enable-the-authenticator)
10. [Step 7 - Define Host Identities](#step-7---define-host-identities)
11. [Step 8 - Grant Hosts to Authenticator and Resources](#step-8---grant-hosts-to-authenticator-and-resources)
12. [Step 9 - Configure Application Workload](#step-9---configure-application-workload)
13. [Step 10 - Test Authentication](#step-10---test-authentication)
14. [Secret Delivery Patterns](#secret-delivery-patterns)
15. [Troubleshooting](#troubleshooting)
16. [Quick Reference](#quick-reference)

---

## 1. Overview

The JWT authenticator (`authn-jwt/kubernetes`) allows Kubernetes workloads to authenticate
to Conjur using their **ServiceAccount JWT tokens** — no API keys needed.

### How It Works

```
┌──────────────────────────────────────────────────────────────────────┐
│  Kubernetes Cluster                                                  │
│                                                                      │
│  ┌─────────────────┐     ┌───────────────────────────────────────┐  │
│  │  K8s API Server  │     │  Pod (namespace: apps)                │  │
│  │                  │     │                                       │  │
│  │  OIDC Endpoint   │◄────│  1. Projected SA Token (JWT)          │  │
│  │  /.well-known/   │     │     mounted at /var/run/secrets/      │  │
│  │  openid-config   │     │     tokens/jwt                        │  │
│  │                  │     │                                       │  │
│  │  JWKS Endpoint   │     │  2. Init/Sidecar reads JWT            │  │
│  │  /openid/v1/jwks │     │     sends to Conjur authn-jwt         │  │
│  └────────┬─────────┘     │                                       │  │
│           │               │  3. Receives Conjur access token       │  │
│           │               │     fetches secrets                    │  │
│           ▼               └────────────────┬──────────────────────┘  │
│  ┌─────────────────┐                       │                         │
│  │  Conjur Server   │◄──────────────────────┘                        │
│  │  (conjur-system)  │                                                │
│  │                  │  Validates JWT signature against JWKS          │
│  │  authn-jwt/      │  Matches sub claim to host annotation         │
│  │  kubernetes      │  Returns Conjur access token                  │
│  └──────────────────┘                                                │
└──────────────────────────────────────────────────────────────────────┘
```

### Kubernetes ServiceAccount JWT Example

```json
{
  "aud": ["https://kubernetes.default.svc.cluster.local"],
  "exp": 1693376769,
  "iat": 1693370769,
  "iss": "https://kubernetes.default.svc.cluster.local",
  "kubernetes.io": {
    "namespace": "apps",
    "pod": {
      "name": "my-app-68db995878-7hg8n",
      "uid": "861be607-ff6f-4bb6-850b-42b842e44a33"
    },
    "serviceaccount": {
      "name": "my-app-sa",
      "uid": "ddb1ca36-0231-4ecc-81b8-25fd0d11a087"
    }
  },
  "nbf": 1693370769,
  "sub": "system:serviceaccount:apps:my-app-sa"
}
```

**Key claim**: The `sub` claim follows the format `system:serviceaccount:<namespace>:<sa-name>`.
This is the value used to match against Conjur host annotations.

---

## 2. Prerequisites

| Requirement | Details |
|-------------|---------|
| Kubernetes | v1.21+ with ServiceAccount token projection |
| Conjur OSS | v1.19+ (JWT authenticator support) |
| CLI tools | `kubectl`, `curl`, `jq` |
| ClusterRole | `system:service-account-issuer-discovery` for JWKS retrieval |
| Conjur admin | Access to load policies and set variables |
| K8s admin | Access to create RBAC, configmaps, deployments |

### Our Environment

| Component | Value |
|-----------|-------|
| Conjur Account | `nimbusConjurAccount` |
| Conjur URL (external) | `http://localhost:30080` |
| Conjur URL (internal) | `http://conjur-server.conjur-system.svc.cluster.local` |
| Conjur Namespace | `conjur-system` |
| App Namespace | `apps` |
| Registry | `localhost:30500` |
| Organization | `nimbus` |
| Environment | `dev` |
| Product | `product1` |
| Identity Path | `nimbus/dev/products/product1/apps` |
| JWT Service ID | `kubernetes` |

---

## 3. Architecture

### Conjur Policy Hierarchy (Our Setup)

```
root
├── conjur/
│   └── authn-jwt/
│       └── kubernetes/           ← JWT Authenticator
│           ├── !webservice
│           ├── jwks-uri
│           ├── token-app-property
│           ├── identity-path
│           ├── issuer
│           ├── audience
│           └── !group authenticatable
│
└── nimbus/                       ← Organization
    └── dev/                      ← Environment
        └── products/
            └── product1/         ← Product
                ├── apps/
                │   ├── nims/
                │   │   └── app1  ← Host (annotated with sub claim)
                │   └── batch/
                │       └── app2  ← Host
                └── resources/
                    └── dbs/
                        └── payments-db/  ← Secret variables
                            ├── host-name
                            ├── username
                            ├── password
                            ├── port
                            └── database-name
```

---

## Step 1 - Retrieve Kubernetes Cluster Information

These values are needed to configure the JWT authenticator variables.

### 1.1 Check OIDC Discovery Endpoint

```bash
kubectl get --raw /.well-known/openid-configuration | jq
```

Expected output:
```json
{
  "issuer": "https://kubernetes.default.svc.cluster.local",
  "jwks_uri": "https://kubernetes.default.svc.cluster.local/openid/v1/jwks",
  "response_types_supported": ["id_token"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"]
}
```

### 1.2 Retrieve the Issuer

```bash
ISSUER=$(kubectl get --raw /.well-known/openid-configuration | jq -r '.issuer')
echo "Issuer: $ISSUER"
```

### 1.3 Retrieve the JWKS URI

```bash
JWKS_URI=$(kubectl get --raw /.well-known/openid-configuration | jq -r '.jwks_uri')
echo "JWKS URI: $JWKS_URI"
```

### 1.4 Verify JWKS Content

```bash
kubectl get --raw /openid/v1/jwks | jq
```

Expected output (RSA public key):
```json
{
  "keys": [
    {
      "use": "sig",
      "kty": "RSA",
      "kid": "qgR3hxR6c9ortKnfd96TK8FfasK-L77vRoPtVz1z91o",
      "alg": "RS256",
      "n": "462bF75dDmlqY...",
      "e": "AQAB"
    }
  ]
}
```

### 1.5 Record Values for Our Setup

| Variable | Value |
|----------|-------|
| JWKS URI | `https://kubernetes.default.svc/openid/v1/jwks` |
| Issuer | `https://kubernetes.default.svc.cluster.local` |
| Audience | `https://kubernetes.default.svc.cluster.local` |
| Token App Property | `sub` |
| Identity Path | `nimbus/dev/products/product1/apps` |

---

## Step 2 - RBAC for JWKS Access

Conjur fetches the JWKS from the Kubernetes API server to validate JWT signatures.
Anonymous access must be allowed on the OIDC endpoints.

### 2.1 Create ClusterRole and ClusterRoleBinding

```bash
kubectl apply -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: oidc-discovery
rules:
- nonResourceURLs:
  - "/.well-known/openid-configuration"
  - "/openid/v1/jwks"
  verbs:
  - get
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: oidc-discovery
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: oidc-discovery
subjects:
- apiGroup: rbac.authorization.k8s.io
  kind: User
  name: system:anonymous
EOF
```

### 2.2 Verify Anonymous Access

```bash
# From inside the cluster (or via kubectl proxy)
curl -sk https://kubernetes.default.svc/openid/v1/jwks | jq .keys[0].kty
# Expected: "RSA"
```

---

## Step 3 - Mount K8s CA into Conjur Pod

The Conjur pod needs to trust the Kubernetes API server's TLS certificate to fetch JWKS over HTTPS.

### 3.1 Extract the K8s CA Certificate

```bash
kubectl get configmap kube-root-ca.crt -n conjur-system -o jsonpath='{.data.ca\.crt}' > k8s-ca.crt
```

### 3.2 Create ConfigMap for the CA Cert

```bash
kubectl create configmap k8s-ca-cert \
  --from-file=k8s-ca.crt \
  -n conjur-system
```

### 3.3 Patch Conjur Deployment

Add an init container that copies the CA cert into the trust store, plus the volume mount:

```yaml
# Add to conjur deployment spec
spec:
  template:
    spec:
      initContainers:
      - name: install-ca
        image: alpine:latest
        command:
        - sh
        - -c
        - |
          cp /k8s-ca/k8s-ca.crt /usr/local/share/ca-certificates/k8s-ca.crt
          update-ca-certificates
          cp -r /etc/ssl/certs/* /conjur-certs/
        volumeMounts:
        - name: k8s-ca
          mountPath: /k8s-ca
        - name: conjur-certs
          mountPath: /conjur-certs
      containers:
      - name: conjur
        volumeMounts:
        - name: conjur-certs
          mountPath: /etc/ssl/certs
      volumes:
      - name: k8s-ca
        configMap:
          name: k8s-ca-cert
      - name: conjur-certs
        emptyDir: {}
```

> **Alternative**: Use `public-keys` variable instead of `jwks-uri` to avoid HTTPS
> fetching entirely. See [Variable Reference](#jwt-authenticator-variables) below.

---

## Step 4 - Create JWT Authenticator Policy

### 4.1 Policy YAML (`authn-jwt-kubernetes.yaml`)

```yaml
- !policy
  id: conjur/authn-jwt/kubernetes
  body:
    - !webservice

    # JWT validation variables (ALL must have values set)
    - !variable jwks-uri
    - !variable token-app-property
    - !variable identity-path
    - !variable issuer
    - !variable audience

    # Group of hosts allowed to authenticate via this JWT authenticator
    - !group authenticatable

    - !permit
      role: !group authenticatable
      privilege: [ read, authenticate ]
      resource: !webservice
```

### 4.2 Load the Policy

```bash
# Get admin token
TOKEN=$(curl -sk -X POST \
  "http://localhost:30080/authn/nimbusConjurAccount/admin/authenticate" \
  -d "rx7j402fwtc9nafbkb02cj5qea37hrr6nj2z98n3s4h36sebndfd" | base64 -w0)

# Load policy (POST = append to root)
curl -sk -X POST \
  "http://localhost:30080/policies/nimbusConjurAccount/policy/root" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "$(cat authn-jwt-kubernetes.yaml)"
```

> **CRITICAL**: Use `POST` (append). NEVER use `PUT` on root — it wipes all sub-policies.

### Using the Admin REST API

```bash
curl -sk -X POST "http://localhost:30086/api/authenticators/jwt/setup" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "kubernetes",
    "jwksUri": "https://kubernetes.default.svc/openid/v1/jwks",
    "tokenAppProperty": "sub",
    "identityPath": "nimbus/dev/products/product1/apps",
    "issuer": "https://kubernetes.default.svc.cluster.local",
    "audience": "https://kubernetes.default.svc.cluster.local"
  }'
```

### Using the Setup Wizard (Web UI)

1. Go to `http://localhost:30087/wizard`
2. Step 3: Select **authn-jwt/kubernetes**
3. Fill in JWKS URI, Token App Property, Identity Path, Issuer, Audience
4. Click **Setup JWT Authenticator**

---

## Step 5 - Set JWT Authenticator Variables

Every variable **MUST** have a value or authentication fails with `CONJ00037E`.

### 5.1 Set Variables via CLI

```bash
TOKEN=$(curl -sk -X POST \
  "http://localhost:30080/authn/nimbusConjurAccount/admin/authenticate" \
  -d "rx7j402fwtc9nafbkb02cj5qea37hrr6nj2z98n3s4h36sebndfd" | base64 -w0)

# jwks-uri
curl -sk -X POST \
  "http://localhost:30080/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Fjwks-uri" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  --data-raw "https://kubernetes.default.svc/openid/v1/jwks"

# token-app-property
curl -sk -X POST \
  "http://localhost:30080/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Ftoken-app-property" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  --data-raw "sub"

# identity-path
curl -sk -X POST \
  "http://localhost:30080/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Fidentity-path" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  --data-raw "nimbus/dev/products/product1/apps"

# issuer
curl -sk -X POST \
  "http://localhost:30080/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Fissuer" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  --data-raw "https://kubernetes.default.svc.cluster.local"

# audience
curl -sk -X POST \
  "http://localhost:30080/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Faudience" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  --data-raw "https://kubernetes.default.svc.cluster.local"
```

### JWT Authenticator Variables

| Variable | Required | Our Value | Description |
|----------|----------|-----------|-------------|
| `jwks-uri` | Yes* | `https://kubernetes.default.svc/openid/v1/jwks` | URL for Conjur to fetch signing keys. Use when K8s OIDC is accessible. |
| `public-keys` | Yes* | _(alternative to jwks-uri)_ | Static JWKS content. Use when K8s OIDC is NOT accessible. Format: `{"type":"jwks","value":<jwks-json>}` |
| `token-app-property` | Yes | `sub` | JWT claim used to identify the host. `sub` = `system:serviceaccount:<ns>:<sa>` |
| `identity-path` | Yes | `nimbus/dev/products/product1/apps` | Conjur policy path prefix. Host is resolved as `<identity-path>/<claim-value>` |
| `issuer` | Yes | `https://kubernetes.default.svc.cluster.local` | Expected `iss` claim in the JWT |
| `audience` | Yes | `https://kubernetes.default.svc.cluster.local` | Expected `aud` claim in the JWT |

> \* One of `jwks-uri` or `public-keys` is required (mutually exclusive). We use `jwks-uri`.

### Using `public-keys` Instead of `jwks-uri` (Alternative)

If Conjur cannot reach the K8s API server HTTPS endpoint (SSL issues), use static keys:

```bash
# Retrieve JWKS and save
JWKS=$(kubectl get --raw /openid/v1/jwks)

# Set as public-keys variable (wrapped in type envelope)
curl -sk -X POST \
  "http://localhost:30080/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Fpublic-keys" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  --data-raw "{\"type\":\"jwks\",\"value\":$JWKS}"
```

> **Note**: When using `public-keys`, you must update the value manually whenever K8s rotates its signing keys.

---

## Step 6 - Enable the Authenticator

The JWT authenticator must be listed in the `CONJUR_AUTHENTICATORS` environment variable on the Conjur server.

### 6.1 Patch the Conjur Deployment

```bash
kubectl -n conjur-system set env deployment/conjur-server \
  CONJUR_AUTHENTICATORS=authn,authn-jwt/kubernetes
```

Or edit the YAML:

```yaml
env:
- name: CONJUR_AUTHENTICATORS
  value: "authn,authn-jwt/kubernetes"
```

### 6.2 Restart Conjur

```bash
kubectl rollout restart deployment conjur-server -n conjur-system
kubectl rollout status deployment conjur-server -n conjur-system --timeout=120s
```

### 6.3 Verify

```bash
curl -s http://localhost:30080/info | jq '.authenticators.configured'
```

Expected:
```json
["authn", "authn-jwt/kubernetes"]
```

---

## Step 7 - Define Host Identities

Each application needs a Conjur **host** with an `authn-jwt/kubernetes/sub` annotation
matching its Kubernetes ServiceAccount `sub` claim.

### 7.1 Host Identity Mapping

The identity is resolved as:

```
identity-path + "/" + token-app-property value
```

For our setup with `identity-path = nimbus/dev/products/product1/apps`:

| App | Service Account | Namespace | sub Claim | Conjur Host Path |
|-----|-----------------|-----------|-----------|-----------------|
| app1 | app1-sa | apps | `system:serviceaccount:apps:app1-sa` | `nimbus/dev/products/product1/apps/nims/app1` |
| app2 | app2-sa | apps | `system:serviceaccount:apps:app2-sa` | `nimbus/dev/products/product1/apps/batch/app2` |

**IMPORTANT**: When `token-app-property = sub`, the `sub` claim value (`system:serviceaccount:apps:app1-sa`)
becomes the host ID under the `identity-path`. So the full host in Conjur is:
`host/nimbus/dev/products/product1/apps/nims/app1`

And the host annotation `authn-jwt/kubernetes/sub` must match the actual `sub` claim value.

### 7.2 Host Policy YAML

```yaml
# Under the appropriate app type policy (e.g., nimbus/dev/products/product1/apps/nims)
- !host
  id: app1
  annotations:
    authn-jwt/kubernetes/sub: "system:serviceaccount:apps:app1-sa"
    description: "NIMS app1 - JWT authenticated"
```

### 7.3 Load via CLI

```bash
curl -sk -X POST \
  "http://localhost:30080/policies/nimbusConjurAccount/policy/nimbus%2Fdev%2Fproducts%2Fproduct1%2Fapps%2Fnims" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode '- !host
  id: app1
  annotations:
    authn-jwt/kubernetes/sub: "system:serviceaccount:apps:app1-sa"'
```

### 7.4 Load via Admin REST API / Wizard

The admin REST API automatically adds the `sub` annotation when `authMethod = kubernetes`:

```bash
curl -sk -X POST "http://localhost:30086/api/hosts/register" \
  -H "Content-Type: application/json" \
  -d '{
    "orgName": "nimbus",
    "environment": "dev",
    "product": "product1",
    "appType": "nims",
    "hostId": "app1",
    "namespace": "apps",
    "serviceAccount": "app1-sa",
    "authMethod": "kubernetes"
  }'
```

> **CONJ00099E**: If a host has NO `authn-jwt/kubernetes/*` annotations, JWT authentication will fail.

---

## Step 8 - Grant Hosts to Authenticator and Resources

### 8.1 Grant to JWT Authenticator Group

Hosts must be members of the `conjur/authn-jwt/kubernetes/authenticatable` group:

```yaml
- !grant
  role: !group conjur/authn-jwt/kubernetes/authenticatable
  member: !host nimbus/dev/products/product1/apps/nims/app1
```

### 8.2 Grant to Resource Readers Group

Hosts need `read` + `execute` on the secret variables:

```yaml
- !grant
  role: !group nimbus/dev/products/product1/resources/dbs/payments-db-readers
  member: !host nimbus/dev/products/product1/apps/nims/app1
```

### 8.3 Load via CLI

```bash
curl -sk -X POST \
  "http://localhost:30080/policies/nimbusConjurAccount/policy/root" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode '- !grant
  role: !group conjur/authn-jwt/kubernetes/authenticatable
  member: !host nimbus/dev/products/product1/apps/nims/app1

- !grant
  role: !group nimbus/dev/products/product1/resources/dbs/payments-db-readers
  member: !host nimbus/dev/products/product1/apps/nims/app1'
```

---

## Step 9 - Configure Application Workload

### 9.1 Create ServiceAccount

```bash
kubectl create serviceaccount app1-sa -n apps
```

### 9.2 Deployment with Projected ServiceAccount Token

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app1
  namespace: apps
spec:
  replicas: 1
  selector:
    matchLabels:
      app: app1
  template:
    metadata:
      labels:
        app: app1
    spec:
      serviceAccountName: app1-sa
      initContainers:
      - name: conjur-init
        image: localhost:30500/conjur-init:latest   # Or cyberark/secrets-provider-for-k8s:latest
        env:
        - name: CONJUR_APPLIANCE_URL
          value: "http://conjur-server.conjur-system.svc.cluster.local"
        - name: CONJUR_ACCOUNT
          value: "nimbusConjurAccount"
        - name: CONJUR_AUTHN_URL
          value: "http://conjur-server.conjur-system.svc.cluster.local/authn-jwt/kubernetes"
        - name: CONJUR_AUTHN_JWT_TOKEN_PATH
          value: "/var/run/secrets/tokens/jwt"
        - name: CONJUR_SECRETS
          value: >-
            nimbus/dev/products/product1/resources/dbs/payments-db/host-name,
            nimbus/dev/products/product1/resources/dbs/payments-db/username,
            nimbus/dev/products/product1/resources/dbs/payments-db/password,
            nimbus/dev/products/product1/resources/dbs/payments-db/port,
            nimbus/dev/products/product1/resources/dbs/payments-db/database-name
        - name: SECRETS_DESTINATION
          value: "/conjur/secrets/credentials.properties"
        volumeMounts:
        - name: jwt-token
          mountPath: /var/run/secrets/tokens
          readOnly: true
        - name: conjur-secrets
          mountPath: /conjur/secrets
      containers:
      - name: app1
        image: localhost:30500/my-app:latest
        volumeMounts:
        - name: conjur-secrets
          mountPath: /conjur/secrets
          readOnly: true
      volumes:
      - name: jwt-token
        projected:
          sources:
          - serviceAccountToken:
              path: jwt
              expirationSeconds: 6000
              audience: "https://kubernetes.default.svc.cluster.local"
      - name: conjur-secrets
        emptyDir:
          medium: Memory
```

### Key Configuration Points

| Config | Value | Why |
|--------|-------|-----|
| `serviceAccountName` | `app1-sa` | Must match the SA in the host annotation |
| `audience` (projected token) | `https://kubernetes.default.svc.cluster.local` | Must match the `audience` variable in Conjur |
| `expirationSeconds` | `6000` | Token lifetime (100 min). Projected tokens auto-rotate. |
| `CONJUR_AUTHN_URL` | `.../authn-jwt/kubernetes` | Points to the JWT authenticator endpoint |
| `CONJUR_AUTHN_JWT_TOKEN_PATH` | `/var/run/secrets/tokens/jwt` | Path to the projected token file |

> **Do NOT set `CONJUR_AUTHN_LOGIN`** for JWT auth — identity comes from the JWT `sub` claim.

### 9.3 CyberArk Secrets Provider (Alternative)

If using the official CyberArk Secrets Provider image:

```yaml
annotations:
  conjur.org/container-mode: init          # or "sidecar" for continuous refresh
  conjur.org/secrets-destination: file     # or "k8s_secrets"
  conjur.org/jwt-token-path: /var/run/secrets/tokens/jwt
  conjur.org/conjur-secrets.db: |
    - host: nimbus/dev/products/product1/resources/dbs/payments-db/host-name
    - user: nimbus/dev/products/product1/resources/dbs/payments-db/username
    - pass: nimbus/dev/products/product1/resources/dbs/payments-db/password
  conjur.org/secret-file-path.db: ./credentials.json
  conjur.org/secret-file-format.db: json
```

---

## Step 10 - Test Authentication

### 10.1 Create a Test Token

```bash
# Generate a token for app1-sa with the correct audience
TOKEN_JWT=$(kubectl create token app1-sa \
  -n apps \
  --audience="https://kubernetes.default.svc.cluster.local" \
  --duration=600s)

echo "JWT Token (decoded):"
echo $TOKEN_JWT | cut -d. -f2 | base64 -d 2>/dev/null | jq .
```

### 10.2 Authenticate Against Conjur

```bash
# Authenticate using the JWT
CONJUR_TOKEN=$(curl -sk -X POST \
  "http://localhost:30080/authn-jwt/kubernetes/nimbusConjurAccount/authenticate" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "jwt=$TOKEN_JWT")

echo "Conjur Token: $CONJUR_TOKEN"
ENCODED=$(echo -n "$CONJUR_TOKEN" | base64 -w0)
```

### 10.3 Fetch a Secret

```bash
curl -sk \
  "http://localhost:30080/secrets/nimbusConjurAccount/variable/nimbus%2Fdev%2Fproducts%2Fproduct1%2Fresources%2Fdbs%2Fpayments-db%2Fusername" \
  -H "Authorization: Token token=\"$ENCODED\""
```

---

## Secret Delivery Patterns

| Pattern | How Secrets Arrive | Rotation | Best For |
|---------|-------------------|----------|----------|
| **Push to File (P2F)** | Init/sidecar writes secrets to shared volume file | Sidecar refreshes periodically | Most apps. No pod restart needed. |
| **Push to K8s Secret (P2S-Env)** | Sidecar updates a K8s Secret; app reads via `env.valueFrom.secretKeyRef` | K8s Secret updated, but env vars need pod restart | Apps that read config from env vars |
| **Push to K8s Secret (P2S-Vol)** | Sidecar updates a K8s Secret; app reads via volume mount | K8s Secret updated; kubelet propagates to volume | Apps that read config from files |
| **Custom Init Container** | Init container authenticates, fetches secrets, writes to shared volume | One-time at pod start | Simple deployments, our `conjur-init` pattern |

### Our Pattern: Custom Init Container

We use a custom Alpine-based init container (`conjur-init`) that:
1. Reads the projected SA JWT from `/var/run/secrets/tokens/jwt`
2. Authenticates to Conjur via `POST /authn-jwt/kubernetes/.../authenticate`
3. Uses the Conjur token to fetch each secret variable
4. Writes secrets to a shared `emptyDir` volume as a properties file
5. The main app container reads the properties file

---

## Troubleshooting

### CONJ00037E: Missing value for resource

```
CONJ00037E Missing value for resource: nimbusConjurAccount:variable:conjur/authn-jwt/kubernetes/audience
```

**Cause**: A variable exists in the policy but has no value set.
**Fix**: Set ALL 5 variables. If you don't need one, remove it from the policy — don't leave it empty.

### CONJ00099E: Role must have relevant annotation

```
CONJ00099E Role must have at least one relevant annotation
```

**Cause**: The host has no `authn-jwt/kubernetes/*` annotation.
**Fix**: Add `authn-jwt/kubernetes/sub` annotation to the host:
```yaml
annotations:
  authn-jwt/kubernetes/sub: "system:serviceaccount:apps:app1-sa"
```

### CONJ00087E: Failed to fetch JWKS (SSL)

**Cause**: Conjur can't verify K8s API server TLS cert.
**Fix**: Either mount K8s CA cert into Conjur pod (Step 3), or use `public-keys` instead of `jwks-uri`.

### JWKS 403 Forbidden

**Cause**: K8s RBAC blocks anonymous access to OIDC endpoints.
**Fix**: Create the ClusterRoleBinding for `system:anonymous` (Step 2).

### Identity Not Found

```
CONJ00007E host not found
```

**Cause**: The host path from `identity-path` + `sub` claim doesn't match any host.
**Fix**: Verify the path resolves correctly:
- `identity-path`: `nimbus/dev/products/product1/apps`
- Host policy path: `nimbus/dev/products/product1/apps/nims/app1`
- The `sub` claim must match the host annotation, not the path

> **Note**: When `token-app-property = sub`, Conjur uses the `sub` claim to find
> the host via annotation matching. The host annotation `authn-jwt/kubernetes/sub`
> must have the exact value of the JWT's `sub` claim.

### Host Not Authorized

**Cause**: Host is not a member of `conjur/authn-jwt/kubernetes/authenticatable`.
**Fix**: Add the grant:
```yaml
- !grant
  role: !group conjur/authn-jwt/kubernetes/authenticatable
  member: !host nimbus/dev/products/product1/apps/nims/app1
```

### Token Audience Mismatch

**Cause**: Projected token `audience` doesn't match the Conjur `audience` variable.
**Fix**: Ensure both are the same value (e.g., `https://kubernetes.default.svc.cluster.local`).

### Nested JWT Claims Don't Work in Annotations

**Cause**: Using `kubernetes.io/namespace` as annotation claim — nested claims may not work.
**Fix**: Use top-level `sub` claim instead. The `sub` claim already contains namespace and SA info:
`system:serviceaccount:<namespace>:<sa-name>`

---

## Quick Reference

### Complete Setup Checklist

- [ ] **K8s Admin**: Retrieve JWKS URI, issuer, audience from cluster OIDC config
- [ ] **K8s Admin**: Create RBAC for anonymous JWKS access
- [ ] **K8s Admin**: Mount K8s CA cert into Conjur pod (or use `public-keys`)
- [ ] **Conjur Admin**: Load JWT authenticator policy (`conjur/authn-jwt/kubernetes`)
- [ ] **Conjur Admin**: Set ALL 5 JWT variables (no empty values!)
- [ ] **Conjur Admin**: Enable authenticator via `CONJUR_AUTHENTICATORS=authn,authn-jwt/kubernetes`
- [ ] **Conjur Admin**: Restart Conjur server
- [ ] **Conjur Admin**: Create host with `authn-jwt/kubernetes/sub` annotation
- [ ] **Conjur Admin**: Grant host to `authenticatable` group
- [ ] **Conjur Admin**: Grant host to resource readers group
- [ ] **App Dev**: Create ServiceAccount in app namespace
- [ ] **App Dev**: Deploy with projected SA token volume (matching audience)
- [ ] **App Dev**: Configure init/sidecar container with `CONJUR_AUTHN_JWT_TOKEN_PATH`
- [ ] **Verify**: Test authentication with `kubectl create token` + `curl`

### Key URLs

| Endpoint | URL |
|----------|-----|
| Conjur Info | `http://localhost:30080/info` |
| JWT Authenticate | `POST http://localhost:30080/authn-jwt/kubernetes/nimbusConjurAccount/authenticate` |
| Fetch Secret | `GET http://localhost:30080/secrets/nimbusConjurAccount/variable/<url-encoded-id>` |
| Admin REST API | `http://localhost:30086/api/...` |
| Web UI | `http://localhost:30087/wizard` |

### Key Files in This Project

| File | Purpose |
|------|---------|
| `k8s/conjur/conjur.yaml` | Conjur server deployment |
| `k8s/apps/conjur-microprofile-rest.yaml` | Admin REST API deployment |
| `conjur-microprofile-rest/.../AuthenticatorResource.java` | JWT setup API |
| `conjur-microprofile-rest/.../PolicyGenerator.java` | Generates host policies with `sub` annotation |
| `conjur-microprofile-rest/.../HostResource.java` | Host registration with `authMethod` |
| `conjur-microprofile-web/.../WizardPageResource.java` | Setup wizard (Step 3 = JWT auth) |

---

## References

- [CyberArk: JWT-based Kubernetes Authentication](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/k8s-ocp/k8s-jwt-authn.htm)
- [CyberArk: Set up Workloads (JWT)](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/k8s-ocp/k8s-jwt-set-up-apps.htm)
- [CyberArk: Workload Identity for K8s](https://docs.cyberark.com/conjur-open-source/latest/en/content/integrations/k8s-ocp/k8s-app-identity-jwt.htm)
- [joetanx/conjur-k8s Reference Implementation](https://github.com/joetanx/conjur-k8s)
- [CyberArk Secrets Provider for K8s](https://github.com/cyberark/secrets-provider-for-k8s)
- [Conjur JWT Authenticator Design Doc](https://github.com/cyberark/conjur/blob/master/design/authenticators/authn_jwt/authn_jwt_solution_design.md)
