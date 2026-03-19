#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# setup-workspace.sh
#
# Replaces FwrkSetup.groovy + the "Load Properties" and "Setup Workspace"
# Jenkins stages.  Clones the staging repo, sparse-checkouts helm override
# layers, validates the image id, and discovers the target namespace.
#
# Outputs:
#   deploy.env           — dotenv file consumed by the deploy job
#   nimbus-staging-pom/  — staging POM for maven plugin execution
#   <app>-ocp-config/    — helm chart layers (base-chart, env-overrides, app-overrides)
# ---------------------------------------------------------------------------
set -euo pipefail

# ── Required environment variables ──────────────────────────────────────────
: "${APP_NAME:?APP_NAME is required}"
: "${ENV_NAME:?ENV_NAME is required}"
: "${IMAGE_ID:?IMAGE_ID is required}"
: "${CI_JOB_TOKEN:?CI_JOB_TOKEN is required (set automatically by GitLab)}"
: "${CI_SERVER_HOST:?CI_SERVER_HOST is required (set automatically by GitLab)}"

# ── Configurable defaults ──────────────────────────────────────────────────
STAGING_REPO_PATH="${STAGING_REPO_PATH:-DTF/framework/nimbus/nimbus-fwrkapp-staging}"
HELM_REPO_PATH="${HELM_REPO_PATH:-DTF/framework/nimbus/devops/nimbus-devops-fwrk-overrides}"
GIT_BRANCH="${GIT_BRANCH:-main}"
HELM_DEFAULTS_PATH="${HELM_DEFAULTS_PATH:-/default/}"
HELM_APPS_PATH="${HELM_APPS_PATH:-/apps/}"

# ── Derived values ─────────────────────────────────────────────────────────
APP_LOWER="$(echo "${APP_NAME}" | tr '[:upper:]' '[:lower:]')"
ENV_UPPER="$(echo "${ENV_NAME}" | tr '[:lower:]' '[:upper:]')"
OCP_CONFIG_DIR="${APP_LOWER}-ocp-config"

# ── Resolve environment-prefixed CI/CD variables ───────────────────────────
# Maps ${ENV_UPPER}_VAR_NAME → VAR_NAME so the rest of the pipeline can use
# generic names regardless of which environment is being deployed.
resolve_env_vars() {
    local prefix="${ENV_UPPER}_"

    eval "OCP_URL=\${${prefix}OCP_URL:?${prefix}OCP_URL is required}"
    eval "OCP_TOKEN=\${${prefix}OCP_TOKEN:?${prefix}OCP_TOKEN is required}"
    eval "NIMBUS_PLUGIN_VER=\${${prefix}NIMBUS_PLUGIN_VER:?${prefix}NIMBUS_PLUGIN_VER is required}"
    eval "SECURITY_PLUGIN_VER=\${${prefix}SECURITY_PLUGIN_VER:?${prefix}SECURITY_PLUGIN_VER is required}"
    eval "COMMON_PLUGIN_VER=\${${prefix}COMMON_PLUGIN_VER:?${prefix}COMMON_PLUGIN_VER is required}"

    echo "Resolved variables for environment '${ENV_NAME}':"
    echo "  OCP_URL            = ${OCP_URL}"
    echo "  NIMBUS_PLUGIN_VER  = ${NIMBUS_PLUGIN_VER}"
    echo "  SECURITY_PLUGIN_VER= ${SECURITY_PLUGIN_VER}"
    echo "  COMMON_PLUGIN_VER  = ${COMMON_PLUGIN_VER}"
}

# ── Git helpers ────────────────────────────────────────────────────────────
git_url() {
    echo "https://gitlab-ci-token:${CI_JOB_TOKEN}@${CI_SERVER_HOST}/${1}.git"
}

clone_repo() {
    local repo_path="$1"
    local branch="$2"
    local target_dir="$3"

    echo "Cloning ${repo_path} (branch: ${branch}) → ${target_dir}/"
    git clone --branch "${branch}" --single-branch --depth 1 \
        "$(git_url "${repo_path}")" "${target_dir}"
}

sparse_checkout() {
    local repo_path="$1"
    local branch="$2"
    local sparse_path="$3"
    local target_dir="$4"

    echo "Sparse checkout ${repo_path} path='${sparse_path}' → ${target_dir}/"
    mkdir -p "${target_dir}"
    git clone --branch "${branch}" --single-branch --depth 1 \
        --no-checkout --filter=blob:none \
        "$(git_url "${repo_path}")" "${target_dir}"

    pushd "${target_dir}" > /dev/null
    git sparse-checkout init --cone
    git sparse-checkout set "${sparse_path}"
    git checkout "${branch}"
    popd > /dev/null
}

flatten_directory() {
    local dir="$1"
    echo "Flattening ${dir}/ into workspace root..."
    cp -r "${dir}"/* ./
    rm -rf "${dir}"
}

# ── Maven helpers ──────────────────────────────────────────────────────────
validate_image() {
    echo "Validating image ID '${IMAGE_ID}' for ${APP_NAME}..."
    pushd nimbus-staging-pom > /dev/null
    mvn -DNIMBUS_MAVEN_CODEGEN_ENV="${ENV_UPPER}" \
        -DappId="${APP_NAME}" \
        -DimageId="${IMAGE_ID}" \
        "gov.nystax.nimbus:nimbus-devops-common-plugin:${COMMON_PLUGIN_VER}:check_image_id_validity"
    popd > /dev/null
}

discover_namespace() {
    echo "Discovering namespace for ${APP_NAME}..."
    pushd nimbus-staging-pom > /dev/null
    mvn -DNIMBUS_MAVEN_CODEGEN_ENV="${ENV_UPPER}" \
        -DAPPLICATION="${APP_NAME}" \
        "gov.nystax.nimbus:nimbus-maven-fwrkapp-plugin:${NIMBUS_PLUGIN_VER}:fwrk-app-config-mojo"

    if [ ! -f appConfig.properties ]; then
        echo "ERROR: appConfig.properties not generated" >&2
        exit 1
    fi

    NAMESPACE="$(grep '^namespace=' appConfig.properties | cut -d'=' -f2)"
    echo "appConfig.properties contents:"
    cat appConfig.properties
    echo "Discovered namespace: ${NAMESPACE}"
    popd > /dev/null
}

# ── Dotenv output ──────────────────────────────────────────────────────────
write_dotenv() {
    local override_exists="false"
    if [ -f "${OCP_CONFIG_DIR}/app-overrides/apps/${APP_LOWER}/${ENV_NAME}/values.yaml" ]; then
        override_exists="true"
    fi

    cat > deploy.env <<EOF
APP_NAME=${APP_NAME}
APP_LOWER=${APP_LOWER}
ENV_NAME=${ENV_NAME}
ENV_UPPER=${ENV_UPPER}
IMAGE_ID=${IMAGE_ID}
MIG_RLS_NMBR=${MIG_RLS_NMBR:-}
DEPLOY_TIME=$(date +%Y%m%d%H%M)
OCP_URL=${OCP_URL}
OCP_TOKEN=${OCP_TOKEN}
NIMBUS_PLUGIN_VER=${NIMBUS_PLUGIN_VER}
SECURITY_PLUGIN_VER=${SECURITY_PLUGIN_VER}
NAMESPACE=${NAMESPACE}
OCP_CONFIG_DIR=${OCP_CONFIG_DIR}
OVERRIDE_YAML_EXISTS=${override_exists}
ARTIFACT_URL=${ARTIFACT_URL:-}
EOF

    echo "Dotenv written to deploy.env"
}

# ── Main ───────────────────────────────────────────────────────────────────
main() {
    echo "========================================"
    echo "Setup Workspace"
    echo "  APP_NAME : ${APP_NAME}"
    echo "  ENV_NAME : ${ENV_NAME}"
    echo "  IMAGE_ID : ${IMAGE_ID}"
    echo "========================================"

    resolve_env_vars

    # 1. Clone staging repo and flatten into workspace root
    clone_repo "${STAGING_REPO_PATH}" "${GIT_BRANCH}" "staging"
    flatten_directory "staging"

    # 2. Sparse-checkout helm chart layers
    sparse_checkout "${HELM_REPO_PATH}" "${GIT_BRANCH}" \
        "${HELM_DEFAULTS_PATH}helm-chart" \
        "${OCP_CONFIG_DIR}/base-chart"

    sparse_checkout "${HELM_REPO_PATH}" "${GIT_BRANCH}" \
        "${HELM_DEFAULTS_PATH}${ENV_NAME}" \
        "${OCP_CONFIG_DIR}/env-overrides"

    sparse_checkout "${HELM_REPO_PATH}" "${GIT_BRANCH}" \
        "${HELM_APPS_PATH}${APP_LOWER}/${ENV_NAME}" \
        "${OCP_CONFIG_DIR}/app-overrides"

    # 3. Validate image and discover namespace
    validate_image
    discover_namespace

    # 4. Write dotenv for deploy job
    write_dotenv

    echo "Setup complete."
}

main "$@"
