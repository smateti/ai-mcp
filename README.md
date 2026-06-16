# GitLab CI/CD Pipeline Architecture — Jenkins Migration Sample

This project demonstrates a GitLab CI/CD pipeline architecture designed for migrating ~600 applications from Jenkins. It implements the **shared pipeline pattern**: one pipeline definition per application type, parameterized by `APP_NAME` and `ENVIRONMENT`.

## Architecture Overview

```
┌─────────────────────┐     ┌──────────────────────────┐     ┌─────────────────────┐
│  Build Console       │     │  Build Trigger           │     │  GitLab             │
│  Web Application     │────▶│  Service (REST API)      │────▶│  (CI/CD Engine)     │
│  (JSP / Servlets)    │HTTP │  (MicroProfile)          │HTTPS│                     │
│  :9082               │     │  :9081                   │     │  :9043              │
└─────────────────────┘     └──────────┬───────────────┘     └────────┬────────────┘
                                        │                              │
                                        ▼                              ▼
                               ┌────────────────┐           ┌─────────────────────┐
                               │ Embedded Derby  │           │  GitLab Runner      │
                               │ (Build History) │           │  (Docker executor)  │
                               └────────────────┘           └─────────┬───────────┘
                                                                       │
                                                               ┌───────▼──────────┐
                                                               │ Trigger Repos    │
                                                               │ app-type-service/│
                                                               │ (include: ...)   │
                                                               └───────┬──────────┘
                                                                       │
                                                               ┌───────▼──────────┐
                                                               │ Central Templates│
                                                               │ pipeline-        │
                                                               │   templates/     │
                                                               │ scripts/         │
                                                               │ registry.json    │
                                                               └──────────────────┘
```

### Key Design Principles

1. **Trigger repos have no source code.** They exist only to provide a GitLab pipeline entry point. One trigger repo per application type (e.g., `service`, `batch`, `ai-app`).

2. **Pipelines are shared.** The real pipeline logic lives in `pipeline-templates/`. Trigger repos `include:` the appropriate template. Updating the template updates all apps of that type.

3. **The pipeline is a traffic controller.** Heavy lifting happens in Python scripts and Maven. The YAML sequences stages, manages artifacts, and handles GitLab-specific concerns (caching, environments, rules).

4. **Application metadata is registry-driven.** `registry.json` maps `APP_NAME` to its services, Maven coordinates, Helm overrides, and post-deploy resources. The pipeline looks up the app and acts accordingly.

5. **Artifacts replace the Jenkins workspace.** Each stage writes JSON/YAML/properties files as GitLab artifacts. Downstream stages consume them via `dependencies:`. Artifacts are kept with `when: always` so developers can download and debug failures.

## Project Structure

```
├── README.md                           ← you are here
│
├── build-trigger-service/              ← REST BACKEND (MicroProfile)
│   ├── pom.xml
│   ├── Dockerfile
│   ├── helm/values.yaml
│   └── src/main/java/com/example/buildtrigger/
│       ├── model/       Application, BuildRecord, BuildRequest, BuildResponse,
│       │                JobInfo, ErrorResponse, GitLabException
│       ├── registry/    ApplicationRegistry (loads registry.json)
│       ├── repository/  BuildRepository (JPA / Derby)
│       ├── gitlab/      GitLabClient (JAX-RS Client → GitLab API)
│       ├── rest/        BuildTriggerApplication, ApplicationResource,
│       │                BuildResource, CorsFilter, GitLabExceptionMapper
│       └── health/      LivenessCheck, ReadinessCheck
│
├── build-console-webapp/               ← WEB UI (JSP / Servlets)
│   ├── pom.xml
│   ├── Dockerfile
│   ├── helm/values.yaml
│   └── src/main/
│       ├── java/com/example/console/
│       │   ├── client/  BuildServiceClient
│       │   └── servlet/ ApplicationsServlet, TriggerBuildServlet,
│       │                HistoryServlet, JobsServlet
│       └── webapp/
│           ├── css/style.css
│           └── WEB-INF/views/ header.jsp, footer.jsp, applications.jsp,
│                              history.jsp, jobs.jsp
│
├── pipeline-templates/                 ← CENTRAL TEMPLATE REPO
│   ├── templates/
│   │   └── service-pipeline.yml        ← full reusable pipeline for "service" type
│   ├── scripts/
│   │   ├── lookup_registry.py          ← reads registry, writes app-metadata.json
│   │   ├── generate_helm_values.py     ← merges base + env + registry → values-final.yaml
│   │   └── post_deploy_resources.py    ← creates queues/listeners after deploy (mock)
│   ├── registry.json                   ← mock application registry
│   ├── docker/
│   │   └── Dockerfile                  ← custom runner image (Java 21, Maven, Python, oc, helm)
│   └── local-test/
│       ├── docker-compose.yml          ← local testing harness
│       ├── .env.example                ← template for local env vars
│       ├── run-local.sh                ← simulates pipeline stages locally
│       └── .gitignore                  ← ignores .env and .secrets/
│
├── app-type-service/                   ← TRIGGER REPO for "service" type
│   ├── .gitlab-ci.yml                  ← minimal; includes template, declares variables
│   └── README.md                       ← API trigger examples, query examples
│
└── sample-service/                     ← SAMPLE MICROPROFILE APP
    ├── pom.xml                         ← Java 21, MicroProfile 6.x, Open Liberty
    ├── Dockerfile                      ← multi-stage build → Open Liberty runtime
    ├── src/main/java/com/example/hello/
    │   ├── HelloApplication.java       ← JAX-RS @ApplicationPath("/api")
    │   ├── HelloResource.java          ← GET /api/hello
    │   ├── GreetingService.java        ← CDI bean with MicroProfile Config
    │   ├── HealthReadyCheck.java       ← /health/ready
    │   └── HealthLiveCheck.java        ← /health/live
    └── helm/
        ├── Chart.yaml
        ├── values.yaml                 ← base values (all environments)
        ├── values-dev.yaml             ← dev overrides
        ├── values-prod.yaml            ← prod overrides
        └── templates/
            ├── deployment.yaml
            ├── service.yaml
            ├── route.yaml              ← OpenShift Route
            └── configmap.yaml
```

## REST API (build-trigger-service)

| Method | Path                          | Description                    |
|--------|-------------------------------|--------------------------------|
| GET    | `/api/applications`           | List registered applications   |
| POST   | `/api/builds`                 | Trigger a new pipeline build   |
| GET    | `/api/builds?appName=X`       | Get build history              |
| GET    | `/api/builds/{id}/jobs`       | Get jobs for a pipeline        |
| GET    | `/api/builds/{id}/refresh`    | Refresh pipeline status        |
| GET    | `/openapi/ui`                 | Swagger UI                     |

## Pipeline Stages

| Stage        | What it does                                                           | Key artifacts produced         |
|--------------|------------------------------------------------------------------------|-------------------------------|
| `prepare`    | Looks up APP_NAME in registry.json; writes metadata + build properties | `app-metadata.json`, `build.properties` |
| `build`      | Clones service source, runs `mvn package -DskipTests`                  | `target/*.war`                |
| `test`       | Runs `mvn test`; publishes JUnit XML for GitLab MR widget              | `surefire-reports/`           |
| `package`    | Builds container image with buildah; pushes to GitLab Container Registry | `image-ref.env`             |
| `deploy`     | Generates `values-final.yaml`; runs `helm upgrade --install`           | `values-final.yaml`, `deploy-receipt.json` |
| `post-deploy`| Creates queues/listeners (mocked)                                      | `resources-created.json`      |

## Quick Start

### 1. Run with mock mode (no GitLab required)

```bash
# Terminal 1: Start the REST backend
cd build-trigger-service
mvn liberty:dev

# Terminal 2: Start the web UI
cd build-console-webapp
mvn liberty:dev
```

- Swagger UI: http://localhost:9081/openapi/ui
- Web Console: http://localhost:9082

### 2. Connect to a real GitLab instance

Edit `build-trigger-service/src/main/resources/META-INF/microprofile-config.properties`:

```properties
gitlab.url=https://localhost:9043
gitlab.token=glpat-YOUR_TOKEN_HERE
gitlab.mock=false
```

### 3. Test pipelines locally with docker-compose

```bash
cd pipeline-templates/local-test
cp .env.example .env
# Edit .env with APP_NAME, ENVIRONMENT, etc.
docker-compose run --rm pipeline bash /workspace/pipeline-templates/local-test/run-local.sh
```

### 4. Trigger a pipeline via GitLab API

```bash
curl --request POST \
  "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/pipeline" \
  --header "PRIVATE-TOKEN: ${PRIVATE_TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{
    "ref": "main",
    "variables": [
      {"key": "APP_NAME", "value": "sample-hello"},
      {"key": "ENVIRONMENT", "value": "dev"}
    ]
  }'
```

## Technology Stack

| Layer            | Technology                                          |
|------------------|-----------------------------------------------------|
| Language         | Java 21                                             |
| Framework        | MicroProfile 6.1, Jakarta EE 10                    |
| Runtime          | Open Liberty                                        |
| Build            | Maven                                               |
| Database         | Embedded Derby (swappable to DB2)                   |
| UI               | JSP 3.1, JSTL, clean CSS                           |
| API Docs         | MicroProfile OpenAPI (Swagger UI)                   |
| CI/CD            | GitLab CI/CD with shared pipeline templates         |
| Container        | Open Liberty on UBI (multi-stage Docker builds)     |
| Orchestration    | OpenShift / Kubernetes with Helm charts             |
| Pipeline Scripts | Python 3 (registry lookup, Helm values, post-deploy)|
