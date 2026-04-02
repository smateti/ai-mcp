# Conjur Secrets Management for OpenLiberty Microservices on Kubernetes

## Executive Summary

This document describes the design for integrating CyberArk Conjur OSS with ~1000 OpenLiberty
microservices across 20-50 product teams on Kubernetes. It covers:

- **Dual authentication**: API key (`authn`) + JWT (`authn-jwt/kubernetes`)
- **Policy hierarchy**: `nimbus/{env}/products/{product}/apps/{type}/{app}` + `resources/{type}/{name}`
- **Hybrid access model**: per-app granular DB access + per-product shared infrastructure secrets
- **Full stack secrets**: DB, Kafka, API keys, SMTP, LDAP, OAuth, certificates
- **3 environments**: dev, qa, prod

---

## Architecture Overview

```
+----------------------------------------------------------------------------------+
|                          Kubernetes Cluster                                       |
|                                                                                  |
|  +------------------------------------------+    +----------------------------+  |
|  |          conjur-system namespace          |    |     apps namespace          |  |
|  |                                           |    |                            |  |
|  |  +-------------+   +------------------+  |    |  +----------------------+  |  |
|  |  | PostgreSQL   |<--| Conjur Server    |  |    |  |  orders-api          |  |  |
|  |  | (data store) |   |                  |<-+----+--| (OpenLiberty)        |  |  |
|  |  +-------------+   | authn             |  |    |  |                      |  |  |
|  |                     | authn-jwt/kubernetes|  |    |  |  JWT --> Conjur     |  |  |
|  |                     +------------------+  |    |  |  Token <-- Secrets   |  |  |
|  |                            ^               |    |  +----------------------+  |  |
|  +----------------------------+--------------+    |                            |  |
|                               |                    |  +----------------------+  |  |
|                               |                    |  |  settlement-api      |  |  |
|  +----------------------------+--------------+    |  |  (OpenLiberty)       |  |  |
|  |  K8s API Server                            |    |  +----------------------+  |  |
|  |  /.well-known/openid-configuration         |    |                            |  |
|  |  /openid/v1/jwks (JWKS endpoint)           |    |  +----------------------+  |  |
|  |                                            |    |  |  billing-monolith    |  |  |
|  |  Issues ServiceAccount JWT tokens          |    |  |  (tWAS, API key)     |  |  |
|  +--------------------------------------------+    |  +----------------------+  |  |
|                                                     +----------------------------+  |
+----------------------------------------------------------------------------------+
```

### Authentication Flow (JWT)

```
1. Pod starts -> SA token auto-mounted at /var/run/secrets/kubernetes.io/serviceaccount/token
2. conjur-microprofile-lib reads token + calls POST /authn-jwt/kubernetes/nimbusConjurAccount/authenticate
3. Conjur validates JWT against K8s JWKS endpoint
4. Conjur maps JWT claims (namespace, serviceaccount) -> host identity
5. Conjur returns short-lived access token
6. Library fetches secrets using access token
7. Secrets injected as MicroProfile Config properties + System properties
8. Liberty server.xml resolves ${db.username}, ${kafka.bootstrap.servers}, etc.
```

### Authentication Flow (API Key)

```
1. Pod starts -> reads CONJUR_AUTHN_LOGIN + CONJUR_AUTHN_API_KEY from K8s Secret
2. conjur-microprofile-lib calls POST /authn/nimbusConjurAccount/{login}/authenticate
3. Conjur validates API key -> returns access token
4. (steps 6-8 same as JWT)
```

---

## Policy Hierarchy

```
root
+-- conjur/
|   +-- authn-jwt/
|       +-- kubernetes/                          <- JWT authenticator config
|           +-- jwks-uri                         (K8s OIDC JWKS endpoint)
|           +-- token-app-property               (JWT claim for identity, e.g. "sub")
|           +-- identity-path                    (base path for host lookup)
|           +-- issuer                           (expected JWT issuer)
|           +-- !webservice                      (authenticator endpoint)
|           +-- authenticatable (group)          (hosts allowed to use JWT)
|
+-- nimbus/
    +-- dev/
    |   +-- products/
    |       +-- payments/
    |       |   +-- apps/
    |       |   |   +-- openshift/               <- JWT-auth hosts
    |       |   |   |   +-- orders-api
    |       |   |   |   +-- settlement-api
    |       |   |   +-- batch/                   <- API-key-auth hosts
    |       |   |   |   +-- settlement-job
    |       |   |   +-- websphere/               <- API-key-auth hosts
    |       |   |       +-- billing-monolith
    |       |   +-- resources/
    |       |   |   +-- dbs/                     <- Per-app granular access
    |       |   |   |   +-- orders-db/
    |       |   |   |   |   +-- url, username, password, port, ...
    |       |   |   |   |   +-- readers (group)  <- only orders-api
    |       |   |   |   +-- ledger-db/
    |       |   |   |       +-- url, username, password, port, ...
    |       |   |   |       +-- readers (group)  <- only settlement-api
    |       |   |   +-- kafka/                   <- Product-wide shared
    |       |   |   |   +-- main/
    |       |   |   |       +-- bootstrap-servers, sasl-username, ...
    |       |   |   |       +-- (accessed via resources-readers layer)
    |       |   |   +-- api/                     <- Product-wide shared
    |       |   |   |   +-- stripe/ (key, secret, webhook-secret)
    |       |   |   +-- smtp/ ldap/ oauth/ certs/
    |       |   |   +-- ...
    |       |   +-- resources-readers (layer)    <- All product apps
    |       |
    |       +-- customer/
    |       |   +-- (same structure)
    |       +-- ...
    +-- qa/
    |   +-- products/ (same structure, different secret values)
    +-- prod/
        +-- products/ (same structure, different secret values)
```

---

## Dual Authentication

### Why Both Methods?

| Auth Method | `authn` (API Key) | `authn-jwt/kubernetes` (JWT) |
|---|---|---|
| **How it works** | App sends API key -> gets token | App sends SA JWT -> gets token |
| **Identity source** | K8s Secret with API key | Pod's ServiceAccount token (auto-mounted) |
| **Secret distribution** | Must create & manage K8s Secrets | None -- uses built-in SA tokens |
| **Best for** | WebSphere, batch jobs, non-K8s | Kubernetes microservices |
| **Rotation** | Must rotate API keys periodically | Automatic -- SA tokens rotate by K8s |
| **Migration** | Works today (existing) | Target state for K8s apps |

### Conjur Server Configuration

Enable both authenticators in `05-conjur-server.yaml`:

```yaml
- name: CONJUR_AUTHENTICATORS
  value: "authn,authn-jwt/kubernetes"
```

### Shared Library Auto-Detection

The `conjur-microprofile-lib` detects which auth method based on environment variables:

```
IF CONJUR_AUTHN_JWT_SERVICE_ID is set -> use JWT auth (authn-jwt/kubernetes)
ELSE IF CONJUR_AUTHN_API_KEY is set  -> use API key auth (authn)
ELSE -> Conjur disabled (no auth configured)
```

**No code changes in applications** -- only env vars change.

### JWT Authenticator Setup

#### 1. Get K8s OIDC endpoints

```bash
# JWKS URI (public keys for token validation)
kubectl get --raw /.well-known/openid-configuration | jq -r .jwks_uri

# Issuer
kubectl get --raw /.well-known/openid-configuration | jq -r .issuer
```

#### 2. Load JWT authenticator policy

```bash
# Load into conjur/authn-jwt
CONJUR_POD=$(kubectl get pod -l app=conjur-server -n conjur-system -o jsonpath='{.items[0].metadata.name}')

kubectl exec $CONJUR_POD -n conjur-system -- conjurctl policy load conjur/authn-jwt \
  /path/to/conjur-authn-jwt-kubernetes.yml
```

#### 3. Set authenticator variables

```bash
TOKEN=$(curl -s -X POST "http://$CONJUR_URL/authn/nimbusConjurAccount/admin/authenticate" \
  -d "$ADMIN_API_KEY" | base64 -w0)

# Set JWKS URI
curl -s -X POST \
  "http://$CONJUR_URL/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Fjwks-uri" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  -d "https://kubernetes.default.svc/openid/v1/jwks"

# Set token-app-property (which JWT claim identifies the app)
curl -s -X POST \
  "http://$CONJUR_URL/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Ftoken-app-property" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  -d "sub"

# Set identity-path (base path prepended to the claim value)
curl -s -X POST \
  "http://$CONJUR_URL/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Fidentity-path" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  -d "nimbus/prod/products"

# Set issuer
curl -s -X POST \
  "http://$CONJUR_URL/secrets/nimbusConjurAccount/variable/conjur%2Fauthn-jwt%2Fkubernetes%2Fissuer" \
  -H "Authorization: Token token=\"$TOKEN\"" \
  -d "https://kubernetes.default.svc"
```

---

## Naming Conventions

### Secret Variable Paths

All secrets follow: `nimbus/{env}/products/{product}/resources/{type}/{resource}/{variable}`

| Type | Path Pattern | Example |
|------|-------------|---------|
| DB URL | `.../resources/dbs/{db}/url` | `nimbus/prod/products/payments/resources/dbs/orders-db/url` |
| DB username | `.../resources/dbs/{db}/username` | `.../dbs/orders-db/username` |
| DB password | `.../resources/dbs/{db}/password` | `.../dbs/orders-db/password` |
| DB port | `.../resources/dbs/{db}/port` | `.../dbs/orders-db/port` |
| DB name | `.../resources/dbs/{db}/database-name` | `.../dbs/orders-db/database-name` |
| DB driver | `.../resources/dbs/{db}/driver-class` | `.../dbs/orders-db/driver-class` |
| Kafka bootstrap | `.../resources/kafka/{cluster}/bootstrap-servers` | `.../kafka/main/bootstrap-servers` |
| Kafka SASL user | `.../resources/kafka/{cluster}/sasl-username` | `.../kafka/main/sasl-username` |
| Kafka SASL pwd | `.../resources/kafka/{cluster}/sasl-password` | `.../kafka/main/sasl-password` |
| Kafka mechanism | `.../resources/kafka/{cluster}/sasl-mechanism` | `.../kafka/main/sasl-mechanism` |
| Kafka protocol | `.../resources/kafka/{cluster}/security-protocol` | `.../kafka/main/security-protocol` |
| Kafka keystore | `.../resources/kafka/{cluster}/keystore-password` | `.../kafka/main/keystore-password` |
| Kafka truststore | `.../resources/kafka/{cluster}/truststore-password` | `.../kafka/main/truststore-password` |
| Schema Registry | `.../resources/kafka/{cluster}/schema-registry-url` | `.../kafka/main/schema-registry-url` |
| API key | `.../resources/api/{service}/key` | `.../api/stripe/key` |
| API secret | `.../resources/api/{service}/secret` | `.../api/stripe/secret` |
| SMTP password | `.../resources/smtp/{server}/password` | `.../smtp/relay/password` |
| LDAP bind pwd | `.../resources/ldap/{server}/bind-password` | `.../ldap/corp/bind-password` |
| OAuth secret | `.../resources/oauth/{provider}/client-secret` | `.../oauth/azure/client-secret` |
| Cert keystore | `.../resources/certs/{name}/keystore-password` | `.../certs/tls/keystore-password` |

### Host Identity Paths

| App Type | Path Pattern | Auth Method |
|----------|-------------|-------------|
| OpenShift microservice | `nimbus/{env}/products/{product}/apps/openshift/{app}` | JWT |
| Batch job | `nimbus/{env}/products/{product}/apps/batch/{app}` | API key |
| WebSphere app | `nimbus/{env}/products/{product}/apps/websphere/{app}` | API key |

### CONJUR_SECRETS Env Var Format

```
CONJUR_SECRETS=configKey1=conjurPath1,configKey2=conjurPath2,...
```

**Example for orders-api:**
```
CONJUR_SECRETS=db.url=nimbus/prod/products/payments/resources/dbs/orders-db/url,db.username=nimbus/prod/products/payments/resources/dbs/orders-db/username,db.password=nimbus/prod/products/payments/resources/dbs/orders-db/password,db.port=nimbus/prod/products/payments/resources/dbs/orders-db/port,db.databaseName=nimbus/prod/products/payments/resources/dbs/orders-db/database-name,kafka.bootstrap.servers=nimbus/prod/products/payments/resources/kafka/main/bootstrap-servers,kafka.sasl.password=nimbus/prod/products/payments/resources/kafka/main/sasl-password,stripe.api.key=nimbus/prod/products/payments/resources/api/stripe/key
```

Liberty resolves these as `${db.url}`, `${db.username}`, `${kafka.bootstrap.servers}`, etc.

---

## Access Model

### Hybrid: Per-App Granular + Per-Product Shared

```
payments/
+-- apps/
|   +-- openshift/
|   |   +-- orders-api      --- granted to: orders-db/readers + resources-readers
|   |   +-- settlement-api  --- granted to: ledger-db/readers + resources-readers
|   +-- batch/
|   |   +-- settlement-job  --- granted to: ledger-db/readers + resources-readers
|   +-- websphere/
|       +-- billing-monolith -- granted to: orders-db/readers + resources-readers
|
+-- resources/
|   +-- dbs/
|   |   +-- orders-db/readers      <- ONLY orders-api + billing-monolith
|   |   +-- ledger-db/readers      <- ONLY settlement-api + settlement-job
|   +-- kafka/main/                <- ALL payments apps (via resources-readers)
|   +-- api/stripe/                <- ALL payments apps (via resources-readers)
|   +-- smtp/ ldap/ oauth/ certs/  <- ALL payments apps (via resources-readers)
|
+-- resources-readers (layer)      <- orders-api, settlement-api, settlement-job, billing-monolith
```

### Access Matrix Example

| App | orders-db | ledger-db | Kafka | Stripe API | SMTP |
|-----|-----------|-----------|-------|-----------|------|
| orders-api | READ | DENIED | READ | READ | READ |
| settlement-api | DENIED | READ | READ | READ | READ |
| settlement-job | DENIED | READ | READ | READ | READ |
| billing-monolith | READ | DENIED | READ | READ | READ |
| customer/profile-api | DENIED | DENIED | DENIED | DENIED | DENIED |

**Key principle:** An app in the `customer` product **cannot** read any `payments` secrets.

---

## Policy Files Reference

All policy files are in `kubernetes/conjur/policies/`.

| # | File | Load Into | Purpose |
|---|------|-----------|---------|
| 1 | `root.yml` | `root` | Top-level branches: conjur/ + nimbus/ |
| 2 | `conjur-authn-jwt-kubernetes.yml` | `conjur/authn-jwt` | JWT authenticator config + variables |
| 3 | `nimbus.yml` | `nimbus` | Environment branches: dev, qa, prod |
| 4 | `env-products-template.yml` | `nimbus/{env}` | Register products under an environment |
| 5 | `product-template.yml` | `nimbus/{env}/products/{product}` | Product structure: apps/ + resources/ + layer |
| 6 | `db-resource-template.yml` | `.../resources/dbs` | DB with variables + readers group |
| 7 | `kafka-resource-template.yml` | `.../resources/kafka` | Kafka cluster variables |
| 8 | `infra-resource-template.yml` | `.../resources/{type}` | SMTP, LDAP, OAuth, certs, API keys |
| 9 | `app-host-template.yml` | `.../apps/{type}` | Register app hosts (with JWT annotations) |
| 10 | `delegation-template.yml` | `nimbus/{env}/products/{product}` | Grant app -> DB + shared resource access |
| 11 | `jwt-enrollment-template.yml` | `root` | Enroll K8s hosts in JWT authenticator |

### Loading Order

```bash
# 1. Root structure
conjurctl policy load root root.yml

# 2. JWT authenticator
conjurctl policy load conjur/authn-jwt conjur-authn-jwt-kubernetes.yml

# 3. Environments
conjurctl policy load nimbus nimbus.yml

# 4. Products
conjurctl policy load nimbus/prod env-products-template.yml

# 5. Product structure (repeat per product)
conjurctl policy load nimbus/prod/products/payments product-template.yml

# 6. Resources (repeat per resource type)
conjurctl policy load nimbus/prod/products/payments/resources/dbs db-resource-template.yml
conjurctl policy load nimbus/prod/products/payments/resources/kafka kafka-resource-template.yml
conjurctl policy load nimbus/prod/products/payments/resources/api infra-resource-template.yml

# 7. App hosts
conjurctl policy load nimbus/prod/products/payments/apps/openshift app-host-template.yml

# 8. Access grants
conjurctl policy load nimbus/prod/products/payments delegation-template.yml

# 9. JWT enrollment (at root level)
conjurctl policy load root jwt-enrollment-template.yml

# 10. Set secret values
curl -X POST .../secrets/.../variable/nimbus%2Fprod%2F.../orders-db%2Fusername -d 'orders_svc'
curl -X POST .../secrets/.../variable/nimbus%2Fprod%2F.../orders-db%2Fpassword -d 'S3cur3P@ss'
```

---

## Application Deployment

### Kubernetes Microservice (JWT Auth)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders-api
  namespace: apps
spec:
  template:
    spec:
      serviceAccountName: orders-api-sa    # Must match host annotation
      containers:
      - name: orders-api
        image: localhost:30500/apps/orders-api:latest
        env:
        # Conjur connection
        - name: CONJUR_APPLIANCE_URL
          value: "http://conjur-server.conjur-system.svc.cluster.local"
        - name: CONJUR_ACCOUNT
          value: "nimbusConjurAccount"
        # JWT auth (no K8s Secret needed)
        - name: CONJUR_AUTHN_JWT_SERVICE_ID
          value: "kubernetes"
        - name: CONJUR_JWT_TOKEN_PATH
          value: "/var/run/secrets/kubernetes.io/serviceaccount/token"
        # Secret mappings
        - name: CONJUR_SECRETS
          value: "db.url=nimbus/prod/products/payments/resources/dbs/orders-db/url,..."
```

### Batch Job (API Key Auth)

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: settlement-job
  namespace: apps
spec:
  schedule: "0 2 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: settlement-job
            env:
            - name: CONJUR_APPLIANCE_URL
              value: "http://conjur-server.conjur-system.svc.cluster.local"
            - name: CONJUR_ACCOUNT
              value: "nimbusConjurAccount"
            # API key auth (from K8s Secret)
            - name: CONJUR_AUTHN_LOGIN
              valueFrom:
                secretKeyRef:
                  name: conjur-settlement-job
                  key: CONJUR_AUTHN_LOGIN
            - name: CONJUR_AUTHN_API_KEY
              valueFrom:
                secretKeyRef:
                  name: conjur-settlement-job
                  key: CONJUR_AUTHN_API_KEY
            - name: CONJUR_SECRETS
              value: "db.url=nimbus/prod/products/payments/resources/dbs/ledger-db/url,..."
```

### WebSphere (API Key Auth)

For traditional WebSphere, store the Conjur API key in the tWAS credential store
or as JVM custom properties. The `conjur-microprofile-lib` JAR can be placed
in the shared library path.

```
# JVM custom properties (tWAS admin console -> Servers -> JVM -> Custom Properties):
CONJUR_APPLIANCE_URL = http://conjur-server.conjur-system.svc.cluster.local
CONJUR_ACCOUNT = nimbusConjurAccount
CONJUR_AUTHN_LOGIN = host/nimbus/prod/products/payments/apps/websphere/billing-monolith
CONJUR_AUTHN_API_KEY = <api-key-from-host-creation>
CONJUR_SECRETS = db.url=nimbus/prod/products/payments/resources/dbs/orders-db/url,...
```

---

## Liberty server.xml Integration

The `conjur-microprofile-lib` sets System properties from CONJUR_SECRETS.
Liberty resolves `${propertyName}` from System properties automatically.

```xml
<!-- DataSource with Conjur-resolved credentials -->
<dataSource id="OrdersDS" jndiName="jdbc/ordersdb">
    <properties.db2.jcc
        serverName="${db.url}"
        portNumber="${db.port}"
        databaseName="${db.databaseName}"
        user="${db.username}"
        password="${db.password}" />
</dataSource>

<!-- Kafka via MicroProfile Config (auto-resolved) -->
<!-- mp.messaging.connector.liberty-kafka.bootstrap.servers=${kafka.bootstrap.servers} -->
```

See `examples/server.xml` for a complete configuration.

---

## Onboarding Workflow

### Onboard a New Product

```bash
# 1. Register the product
conjurctl policy load nimbus/prod/products product-template.yml  # (after editing ID)

# 2. Register databases
conjurctl policy load nimbus/prod/products/{product}/resources/dbs db-resource-template.yml

# 3. Register shared infra (Kafka, API keys, etc.)
conjurctl policy load nimbus/prod/products/{product}/resources/kafka kafka-resource-template.yml
conjurctl policy load nimbus/prod/products/{product}/resources/api infra-resource-template.yml

# 4. Set secret values
curl -X POST .../secrets/.../variable/{path} -d '{value}'

# 5. Register app hosts
conjurctl policy load nimbus/prod/products/{product}/apps/openshift app-host-template.yml

# 6. Grant access
conjurctl policy load nimbus/prod/products/{product} delegation-template.yml

# 7. Enroll JWT hosts (at root)
conjurctl policy load root jwt-enrollment-template.yml

# 8. Deploy the app on Kubernetes with the correct env vars
```

### Onboard a New App to an Existing Product

```bash
# 1. Add the host to apps/{type}
conjurctl policy load nimbus/prod/products/{product}/apps/openshift - <<EOF
- !host
  id: new-service
  annotations:
    authn-jwt/kubernetes/namespace: "apps"
    authn-jwt/kubernetes/serviceaccount: "new-service-sa"
EOF

# 2. Grant DB access (if needed)
conjurctl policy load nimbus/prod/products/{product} - <<EOF
- !grant
  role: !group resources/dbs/orders-db/readers
  member: !host apps/openshift/new-service
- !grant
  role: !layer resources-readers
  member: !host apps/openshift/new-service
EOF

# 3. Enroll in JWT (at root)
conjurctl policy load root - <<EOF
- !grant
  role: !group conjur/authn-jwt/kubernetes/authenticatable
  member: !host nimbus/prod/products/{product}/apps/openshift/new-service
EOF

# 4. Deploy
kubectl apply -f new-service-deployment.yaml
```

---

## Migration Path: API Key -> JWT

For teams currently using API key auth who want to migrate to JWT:

1. **No app code changes** -- only deployment YAML changes
2. Remove `CONJUR_AUTHN_LOGIN` and `CONJUR_AUTHN_API_KEY` env vars
3. Add `CONJUR_AUTHN_JWT_SERVICE_ID` and `CONJUR_JWT_TOKEN_PATH`
4. Ensure the host has JWT annotations and is in the `authenticatable` group
5. Delete the `conjur-{appname}` K8s Secret (no longer needed)

```yaml
# BEFORE (API key auth):
- name: CONJUR_AUTHN_LOGIN
  valueFrom: { secretKeyRef: { name: conjur-myapp, key: CONJUR_AUTHN_LOGIN } }
- name: CONJUR_AUTHN_API_KEY
  valueFrom: { secretKeyRef: { name: conjur-myapp, key: CONJUR_AUTHN_API_KEY } }

# AFTER (JWT auth):
- name: CONJUR_AUTHN_JWT_SERVICE_ID
  value: "kubernetes"
- name: CONJUR_JWT_TOKEN_PATH
  value: "/var/run/secrets/kubernetes.io/serviceaccount/token"
```

---

## Environment Isolation

Each environment (dev, qa, prod) is a completely separate branch:

```
nimbus/dev/products/payments/resources/dbs/orders-db/password   -> "dev-password"
nimbus/qa/products/payments/resources/dbs/orders-db/password    -> "qa-password"
nimbus/prod/products/payments/resources/dbs/orders-db/password  -> "prod-password"
```

- Hosts in `nimbus/dev/...` **cannot** read secrets in `nimbus/prod/...`
- Same policy templates, different secret values per environment
- Deploy the same app YAML across environments by changing the `CONJUR_SECRETS` paths

---

## Security Considerations

1. **Least privilege**: Each app can only read its own DB secrets + product-level shared secrets
2. **Cross-product isolation**: Apps in `payments` cannot read `customer` secrets
3. **Environment isolation**: Dev apps cannot read prod secrets
4. **JWT token validation**: Conjur validates SA tokens against K8s JWKS (public key verification)
5. **Short-lived tokens**: Conjur access tokens expire (default 8 minutes)
6. **No API key sprawl**: JWT auth eliminates the need to distribute/rotate API keys for K8s apps
7. **Audit trail**: Conjur logs all authentication attempts and secret accesses

---

## Files in This Directory

```
kubernetes/conjur/
+-- DEPLOY.md                           <- Kubernetes deployment guide
+-- DESIGN-MICROSERVICES.md             <- This document
+-- 03-rbac.yaml                        <- RBAC (Secret management)
+-- 04-postgres.yaml                    <- PostgreSQL backend
+-- 05-conjur-server.yaml               <- Conjur server
+-- policies/
|   +-- root.yml                        <- Top-level: conjur + nimbus
|   +-- conjur-authn-jwt-kubernetes.yml <- JWT authenticator policy
|   +-- nimbus.yml                      <- dev, qa, prod branches
|   +-- env-products-template.yml       <- Register products
|   +-- product-template.yml            <- Product: apps + resources + layer
|   +-- db-resource-template.yml        <- DB with reader group
|   +-- kafka-resource-template.yml     <- Kafka cluster variables
|   +-- infra-resource-template.yml     <- SMTP, LDAP, OAuth, certs, API
|   +-- app-host-template.yml           <- App hosts (JWT + API key)
|   +-- delegation-template.yml         <- Access grants
|   +-- jwt-enrollment-template.yml     <- JWT authenticator enrollment
+-- examples/
    +-- orders-api-deployment.yaml      <- Full K8s Deployment example
    +-- server.xml                      <- Liberty config with all variables
    +-- onboard-product.json            <- Step-by-step onboarding guide
```
