# Build Trigger Service

MicroProfile REST backend that triggers GitLab CI/CD pipelines and tracks build history.

## Endpoints

| Method | Path                          | Description                    |
|--------|-------------------------------|--------------------------------|
| GET    | `/api/applications`           | List registered applications   |
| POST   | `/api/builds`                 | Trigger a new pipeline build   |
| GET    | `/api/builds?appName=X`       | Get build history              |
| GET    | `/api/builds/{id}/jobs`       | Get jobs for a pipeline        |
| GET    | `/api/builds/{id}/refresh`    | Refresh pipeline status        |
| GET    | `/health/live`                | Liveness probe                 |
| GET    | `/health/ready`               | Readiness probe (checks GitLab)|
| GET    | `/openapi`                    | OpenAPI 3 spec (JSON)          |
| GET    | `/openapi/ui`                 | Swagger UI                     |

## Quick Start

```bash
# Run locally with mock mode (no GitLab required)
mvn liberty:dev

# Open Swagger UI
open http://localhost:9081/openapi/ui
```

## Configuration

All properties are in `src/main/resources/META-INF/microprofile-config.properties`.
Override via environment variables (dots → underscores, uppercase):

| Property                       | Env Variable                    | Default                |
|--------------------------------|---------------------------------|------------------------|
| `gitlab.url`                   | `GITLAB_URL`                    | `https://localhost:9043` |
| `gitlab.token`                 | `GITLAB_TOKEN`                  | (empty)                |
| `gitlab.mock`                  | `GITLAB_MOCK`                   | `true`                 |
| `gitlab.trigger-repo.service`  | `GITLAB_TRIGGER_REPO_SERVICE`   | `2`                    |
| `gitlab.trigger-repo.batch`    | `GITLAB_TRIGGER_REPO_BATCH`     | `3`                    |
| `cors.allowed.origins`         | `CORS_ALLOWED_ORIGINS`          | `*`                    |

## Trigger a Build

```bash
curl -X POST http://localhost:9081/api/builds \
  -H "Content-Type: application/json" \
  -d '{"appName": "sample-hello", "environment": "dev"}'
```

## Technology

- Java 21, MicroProfile 6.1, Open Liberty
- JPA with embedded Derby (swappable to DB2)
- MicroProfile OpenAPI (Swagger UI), Health, Fault Tolerance, Config
- JAX-RS Client for GitLab API communication
