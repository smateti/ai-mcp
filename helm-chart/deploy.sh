#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# deploy.sh — Render Helm templates into plain YAML files
#
# Merge order (last wins):
#   values.yaml                                              (defaults)
#   environments/<env>/values.yaml                           (environment)
#   environments/<env>/namespaces/<ns>/values.yaml           (namespace)
#   environments/<env>/applications/<app>/values.yaml        (application)
#
# Usage:
#   ./deploy.sh <environment> <namespace> <application> [helm-extra-args...]
#
# Output:
#   output/<environment>/<namespace>/<application>.yaml
#
# Examples:
#   ./deploy.sh dev team-alpha backend
#   ./deploy.sh prod team-beta frontend
#   ./deploy.sh dev team-alpha backend --debug
# =============================================================================

CHART_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_DIR="${CHART_DIR}/environments"
OUTPUT_DIR="${CHART_DIR}/output"

ENV="${1:?Usage: $0 <environment> <namespace> <application> [helm-args...]}"
NS="${2:?Usage: $0 <environment> <namespace> <application> [helm-args...]}"
APP="${3:?Usage: $0 <environment> <namespace> <application> [helm-args...]}"
shift 3

RELEASE_NAME="${APP}-${NS}-${ENV}"

# Build the -f chain: defaults → environment → namespace → application
VALUE_FILES=("-f" "${CHART_DIR}/values.yaml")

ENV_VALUES="${ENV_DIR}/${ENV}/values.yaml"
if [[ -f "${ENV_VALUES}" ]]; then
  VALUE_FILES+=("-f" "${ENV_VALUES}")
else
  echo "WARNING: No values file at ${ENV_VALUES}" >&2
fi

NS_VALUES="${ENV_DIR}/${ENV}/namespaces/${NS}/values.yaml"
if [[ -f "${NS_VALUES}" ]]; then
  VALUE_FILES+=("-f" "${NS_VALUES}")
else
  echo "WARNING: No values file at ${NS_VALUES}" >&2
fi

APP_VALUES="${ENV_DIR}/${ENV}/applications/${APP}/values.yaml"
if [[ -f "${APP_VALUES}" ]]; then
  VALUE_FILES+=("-f" "${APP_VALUES}")
else
  echo "WARNING: No values file at ${APP_VALUES}" >&2
fi

# Create output directory
OUT_PATH="${OUTPUT_DIR}/${ENV}/${NS}"
mkdir -p "${OUT_PATH}"
OUT_FILE="${OUT_PATH}/${APP}.yaml"

echo "=== Generating: ${RELEASE_NAME} ==="
echo "  Environment : ${ENV}"
echo "  Namespace   : ${NS}"
echo "  Application : ${APP}"
echo "  Value chain :"
for f in "${VALUE_FILES[@]}"; do
  [[ "$f" != "-f" ]] && echo "    - ${f}"
done
echo ""

helm template "${RELEASE_NAME}" "${CHART_DIR}" \
  --namespace "${NS}" \
  "${VALUE_FILES[@]}" \
  "$@" > "${OUT_FILE}"

echo "  Output      : ${OUT_FILE}"
echo "=== Done ==="
