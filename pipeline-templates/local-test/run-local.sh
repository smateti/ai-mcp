#!/usr/bin/env bash
# =============================================================================
# run-local.sh — Simulate the GitLab CI/CD pipeline locally
# =============================================================================
# Runs the same Python scripts and Maven commands that the YAML pipeline uses,
# in the same order: prepare → build → test.
#
# Package/deploy stages are skipped by default (no registry/cluster locally)
# but can be enabled with --full.
#
# Usage:
#   ./pipeline-templates/local-test/run-local.sh              # prepare + build + test
#   ./pipeline-templates/local-test/run-local.sh --full        # all stages (mocked)
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors for stage headers
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

FULL_RUN=false
if [[ "${1:-}" == "--full" ]]; then
    FULL_RUN=true
fi

stage() {
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  STAGE: $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

success() {
    echo -e "${GREEN}✓ $1${NC}"
}

warn() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

fail() {
    echo -e "${RED}✗ $1${NC}"
    exit 1
}

cd "$REPO_ROOT"

# ── Validate environment ────────────────────────────────────────────────────
echo "Working directory: $(pwd)"
echo "APP_NAME:          ${APP_NAME:-<not set>}"
echo "ENVIRONMENT:       ${ENVIRONMENT:-<not set>}"

if [[ -z "${APP_NAME:-}" ]]; then
    fail "APP_NAME is not set. Source your .env file or export it."
fi

# ── BEFORE_SCRIPT: Certificate + settings.xml injection ─────────────────────
stage "before_script (cert + settings injection)"

if [[ -n "${CA_BUNDLE:-}" ]] && [[ -f "$CA_BUNDLE" ]]; then
    echo "CA_BUNDLE found at $CA_BUNDLE"
    # In a container this would run update-ca-certificates and keytool.
    # Locally we just verify the file exists.
    success "CA bundle file is readable ($(wc -l < "$CA_BUNDLE") lines)"
else
    warn "No CA_BUNDLE set or file not found; skipping cert injection."
fi

if [[ -n "${MAVEN_SETTINGS:-}" ]] && [[ -f "$MAVEN_SETTINGS" ]]; then
    echo "MAVEN_SETTINGS found at $MAVEN_SETTINGS"
    mkdir -p "$HOME/.m2"
    cp "$MAVEN_SETTINGS" "$HOME/.m2/settings.xml"
    success "Copied settings.xml to $HOME/.m2/settings.xml"
else
    warn "No MAVEN_SETTINGS set or file not found; using default settings."
fi

# ── STAGE: prepare ──────────────────────────────────────────────────────────
stage "prepare"

python3 pipeline-templates/scripts/lookup_registry.py
if [[ ! -f app-metadata.json ]] || [[ ! -f build.properties ]]; then
    fail "prepare stage did not produce expected artifacts."
fi
success "app-metadata.json and build.properties created."

# ── STAGE: build ────────────────────────────────────────────────────────────
stage "build"

# Copy build.properties into the Maven resource path
if [[ -d "sample-service/src/main/resources/META-INF" ]]; then
    cp build.properties sample-service/src/main/resources/META-INF/build.properties
    success "Copied build.properties into META-INF/"
fi

MAVEN_CLI_OPTS="--batch-mode --errors --fail-at-end --show-version"

echo "Running: mvn package -DskipTests"
cd sample-service
mvn $MAVEN_CLI_OPTS package -DskipTests
cd "$REPO_ROOT"
success "Maven build completed."

# ── STAGE: test ─────────────────────────────────────────────────────────────
stage "test"

echo "Running: mvn test"
cd sample-service
mvn $MAVEN_CLI_OPTS test
cd "$REPO_ROOT"
success "Tests passed."

# ── STAGE: package (only with --full) ───────────────────────────────────────
if $FULL_RUN; then
    stage "package"
    warn "Skipping container build in local mode (no buildah/registry)."
    echo "IMAGE_TAG=local/hello-service:local" > image-ref.env
    success "Wrote mock image-ref.env"
fi

# ── STAGE: deploy (only with --full) ────────────────────────────────────────
if $FULL_RUN; then
    stage "deploy"
    python3 pipeline-templates/scripts/generate_helm_values.py sample-service/helm/values.yaml
    success "Generated values-final.yaml"
    warn "Skipping helm upgrade in local mode (no cluster)."
    echo '{"appName":"'"$APP_NAME"'","environment":"'"$ENVIRONMENT"'","imageTag":"local","status":"mock"}' > deploy-receipt.json
    success "Wrote mock deploy-receipt.json"
fi

# ── STAGE: post-deploy (only with --full) ────────────────────────────────────
if $FULL_RUN; then
    stage "post-deploy"
    python3 pipeline-templates/scripts/post_deploy_resources.py
    success "Post-deploy resources created (mock)."
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  LOCAL PIPELINE RUN COMPLETE${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "Artifacts produced:"
ls -la app-metadata.json build.properties 2>/dev/null || true
ls -la values-final.yaml deploy-receipt.json resources-created.json image-ref.env 2>/dev/null || true
echo ""
