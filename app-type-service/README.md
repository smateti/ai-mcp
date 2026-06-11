# app-type-service — Trigger Repo

This repository is a **trigger repo** for the `service` application type. It contains no application source code. Its only purpose is to provide a GitLab CI/CD pipeline entry point that our deployment UI calls via the GitLab Pipelines API.

## How it works

1. The custom deployment UI collects `APP_NAME` and `ENVIRONMENT` from the user.
2. The UI calls the GitLab Pipelines API to create a pipeline in **this** repo, passing those variables.
3. This repo's `.gitlab-ci.yml` uses `include:` to pull the full pipeline definition from the central `pipeline-templates` project.
4. The pipeline runs: prepare → build → test → package → deploy → post-deploy.

## Triggering a pipeline via API

### Using `curl`

```bash
# Replace these values:
GITLAB_URL="https://gitlab.example.com"
PROJECT_ID="42"                          # this repo's GitLab project ID
TRIGGER_TOKEN="your-pipeline-trigger-token"  # or use a personal access token

# Trigger a pipeline for the "order-management" app deploying to "staging"
curl --request POST \
  "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/trigger/pipeline" \
  --form "token=${TRIGGER_TOKEN}" \
  --form "ref=main" \
  --form "variables[APP_NAME]=order-management" \
  --form "variables[ENVIRONMENT]=staging"
```

### Using a Personal Access Token (alternative)

```bash
PRIVATE_TOKEN="glpat-xxxxxxxxxxxx"

curl --request POST \
  "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/pipeline" \
  --header "PRIVATE-TOKEN: ${PRIVATE_TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{
    "ref": "main",
    "variables": [
      {"key": "APP_NAME", "value": "order-management"},
      {"key": "ENVIRONMENT", "value": "staging"}
    ]
  }'
```

## Querying pipeline history

### List recent pipelines filtered by variables

GitLab does not natively filter pipelines by variable values, but you can filter by status and then inspect variables:

```bash
# List the last 20 pipelines
curl --header "PRIVATE-TOKEN: ${PRIVATE_TOKEN}" \
  "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/pipelines?per_page=20&order_by=id&sort=desc"

# Get variables for a specific pipeline
curl --header "PRIVATE-TOKEN: ${PRIVATE_TOKEN}" \
  "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/pipelines/${PIPELINE_ID}/variables"
```

### Find deployments for a specific app + environment

Since the pipeline uses `environment:` in the deploy job, you can query GitLab's Environments API:

```bash
# List environments (each ENVIRONMENT value becomes a GitLab environment)
curl --header "PRIVATE-TOKEN: ${PRIVATE_TOKEN}" \
  "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/environments?search=staging"

# List deployments for a specific environment
curl --header "PRIVATE-TOKEN: ${PRIVATE_TOKEN}" \
  "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/environments/${ENV_ID}/deployments"
```

## Available applications

See `pipeline-templates/registry.json` for the full list. Currently registered:

| APP_NAME           | Description                        |
|--------------------|------------------------------------|
| `order-management` | Order Management System            |
| `inventory-system` | Inventory Tracking System          |
| `sample-hello`     | Sample Hello World MicroProfile App|

## Valid ENVIRONMENT values

`dev`, `staging`, `prod`
