#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# deploy.sh — Render Helm templates into plain YAML files
#
# Merge order (last wins):
#   values.yaml                                              (defaults)
#   environments/<env>/values.yaml                           (environment)
#   environments/<env>/products/<product>/values.yaml        (product)
#   environments/<env>/applications/<app>/values.yaml        (application)
#
# Not every layer needs a values file — missing layers are silently skipped.
# Only the base values.yaml (defaults) is required.
#
# Usage:
#   ./deploy.sh <environment> <product> <application> [helm-extra-args...]
#
# Output:
#   output/<environment>/<product>/<application>.yaml
#
# Examples:
#   ./deploy.sh dev team-alpha backend
#   ./deploy.sh prod team-beta frontend
#   ./deploy.sh dev team-alpha backend --debug
# =============================================================================

CHART_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_DIR="${CHART_DIR}/environments"
OUTPUT_DIR="${CHART_DIR}/output"

ENV="${1:?Usage: $0 <environment> <product> <application> [helm-args...]}"
PRODUCT="${2:?Usage: $0 <environment> <product> <application> [helm-args...]}"
APP="${3:?Usage: $0 <environment> <product> <application> [helm-args...]}"
shift 3

RELEASE_NAME="${APP}-${PRODUCT}-${ENV}"

# Build the -f chain: defaults → environment → product → application
# Missing layers are silently skipped (only defaults is required)
VALUE_FILES=("-f" "${CHART_DIR}/values.yaml")
LAYERS_USED=("defaults")

CANDIDATES=(
  "${ENV_DIR}/${ENV}/values.yaml|${ENV}"
  "${ENV_DIR}/${ENV}/products/${PRODUCT}/values.yaml|${ENV}/${PRODUCT}"
  "${ENV_DIR}/${ENV}/applications/${APP}/values.yaml|${ENV}/${APP}"
)

for entry in "${CANDIDATES[@]}"; do
  file="${entry%%|*}"
  label="${entry##*|}"
  if [[ -f "${file}" ]]; then
    VALUE_FILES+=("-f" "${file}")
    LAYERS_USED+=("${label}")
  fi
done

# Create output directory
OUT_PATH="${OUTPUT_DIR}/${ENV}/${PRODUCT}"
mkdir -p "${OUT_PATH}"
OUT_FILE="${OUT_PATH}/${APP}.yaml"

echo "=== Generating: ${RELEASE_NAME} ==="
echo "  Environment : ${ENV}"
echo "  Product     : ${PRODUCT}"
echo "  Application : ${APP}"
echo "  Layers      : ${LAYERS_USED[*]}"
echo ""

helm template "${RELEASE_NAME}" "${CHART_DIR}" \
  --namespace "${PRODUCT}" \
  "${VALUE_FILES[@]}" \
  "$@" > "${OUT_FILE}"

echo "  Output      : ${OUT_FILE}"
echo "=== Done ==="
