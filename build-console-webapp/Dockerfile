# =============================================================================
# Build Console Web Application — Production Container Image
# =============================================================================

# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
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
COPY --chown=1001:0 --from=build /build/target/build-console-webapp.war /config/apps/build-console-webapp.war

# Optional: inject a custom CA certificate at build time
ARG EXTRA_CA_BUNDLE=""
RUN if [ -n "$EXTRA_CA_BUNDLE" ]; then \
      echo "$EXTRA_CA_BUNDLE" > /tmp/extra-ca.crt && \
      keytool -importcert -noprompt -alias extra-ca \
        -keystore /opt/java/openjdk/lib/security/cacerts \
        -storepass changeit -file /tmp/extra-ca.crt && \
      rm /tmp/extra-ca.crt; \
    fi

# Configure SCC (Shared Class Cache) for faster startup
RUN configure.sh
