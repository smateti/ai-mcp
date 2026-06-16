# Build Console Web Application

Server-side JSP web application for triggering and monitoring GitLab CI/CD pipeline builds.

## Pages

| URL                          | Description                              |
|------------------------------|------------------------------------------|
| `/`                          | Application list (landing page)          |
| `/history?appName=X`         | Build history for an application         |
| `/jobs?pipelineId=N`         | Jobs for a specific pipeline             |
| `/trigger` (POST)            | Trigger a new build (redirects to history)|
| `/health/live`               | Liveness probe                           |
| `/health/ready`              | Readiness probe                          |

## Quick Start

```bash
# Start the backend first (build-trigger-service)
cd ../build-trigger-service
mvn liberty:dev

# Then start the webapp (in a separate terminal)
cd ../build-console-webapp
mvn liberty:dev

# Open in browser
open http://localhost:9082
```

## Configuration

| Property           | Env Variable       | Default                  |
|--------------------|--------------------|--------------------------|
| `build.service.url`| `BUILD_SERVICE_URL`| `http://localhost:9081`   |

## Technology

- Java 21, Jakarta Servlet 6.0, JSP 3.1 (Pages), JSTL 3.0
- CDI 4.0 for dependency injection
- JAX-RS Client for calling the backend REST API
- Open Liberty runtime
- Clean CSS (no frameworks)
