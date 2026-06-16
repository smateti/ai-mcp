# =============================================================================
# Sample Service — Production Container Image
# =============================================================================
# Multi-stage build:
#   1. Build stage: compile with Maven
#   2. Runtime stage: slim Open Liberty image with just the WAR
# =============================================================================

# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Download dependencies first (layer caching optimization)
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn package -DskipTests -B

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM icr.io/appcafe/open-liberty:kernel-slim-java21-openj9-ubi

# Copy Liberty server configuration
COPY --chown=1001:0 src/main/liberty/config/server.xml /config/server.xml

# Install required features based on server.xml
RUN features.sh

# Copy the built WAR
COPY --chown=1001:0 --from=build /build/target/hello-service.war /config/apps/hello-service.war

# Configure SCC (Shared Class Cache) for faster startup
RUN configure.sh
