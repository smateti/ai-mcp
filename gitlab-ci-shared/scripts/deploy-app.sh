#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# deploy-app.sh
#
# Replaces FwrkDeploy.groovy.  Runs Maven plugins, manages ConfigMaps,
# renders Helm templates, applies manifests with oc, and waits for rollout.
#
# Expects environment variables set by setup-workspace.sh (via deploy.env):
#   APP_NAME, APP_LOWER, ENV_NAME, ENV_UPPER, IMAGE_ID, MIG_RLS_NMBR,
#   DEPLOY_TIME, OCP_URL, OCP_TOKEN, NIMBUS_PLUGIN_VER, SECURITY_PLUGIN_VER,
#   NAMESPACE, OCP_CONFIG_DIR, OVERRIDE_YAML_EXISTS, ARTIFACT_URL
# ---------------------------------------------------------------------------
set -euo pipefail

ROLLOUT_TIMEOUT_SECONDS="${ROLLOUT_TIMEOUT_SECONDS:-300}"

# ── OCP login ──────────────────────────────────────────────────────────────
login_to_ocp() {
    echo "Logging in to OpenShift at ${OCP_URL}..."
    oc login --token="${OCP_TOKEN}" --server="${OCP_URL}"
}

# ── Maven plugins ──────────────────────────────────────────────────────────
run_maven_plugins() {
    echo "Running Maven plugins..."
    pushd nimbus-staging-pom > /dev/null

    # 1. Security plugin — app-secrets-update
    mvn -DNIMBUS_MAVEN_CODEGEN_ENV="${ENV_UPPER}" \
        -DAPPLICATION="${APP_NAME}" \
        -DocpToken="${OCP_TOKEN}" -DocpUrl="${OCP_URL}" \
        -DimageId="${IMAGE_ID}" -Dnamespace="${NAMESPACE}" \
        -DappType=FWRK-WEBAPP \
        "gov.nystax.nimbus:nimbus-maven-security-plugin:${SECURITY_PLUGIN_VER}:app-secrets-update"

    # 2. Fwrkapp plugin — fwrk-ocp-config-update
    mvn -DNIMBUS_MAVEN_CODEGEN_ENV="${ENV_UPPER}" \
        -DworkspaceDir="${CI_PROJECT_DIR}/${OCP_CONFIG_DIR}" \
        -DappId="${APP_NAME}" \
        -DocpToken="${OCP_TOKEN}" -DocpUrl="${OCP_URL}" \
        -Dnamespace="${NAMESPACE}" \
        "gov.nystax.nimbus:nimbus-maven-fwrkapp-plugin:${NIMBUS_PLUGIN_VER}:fwrk-ocp-config-update"

    # 3. Fwrkapp plugin — build-deploy-report
    mvn -DNIMBUS_MAVEN_CODEGEN_ENV="${ENV_UPPER}" \
        -DAPPLICATION="${APP_NAME}" \
        -DimageId="${IMAGE_ID}" -DmigRlsNmbr="${MIG_RLS_NMBR:-}" \
        "gov.nystax.nimbus:nimbus-maven-fwrkapp-plugin:${NIMBUS_PLUGIN_VER}:build-deploy-report"

    popd > /dev/null
}

# ── ConfigMap management ───────────────────────────────────────────────────
manage_configmap() {
    local base_chart="./base-chart/default/helm-chart"
    local env_overrides=""
    local app_overrides=""

    if [ "${ENV_NAME}" != "dev" ]; then
        env_overrides=" --values ./env-overrides/default/${ENV_NAME}/values.yaml"
    fi
    if [ "${OVERRIDE_YAML_EXISTS}" = "true" ]; then
        app_overrides=" --values ./app-overrides/apps/${APP_LOWER}/${ENV_NAME}/values.yaml"
    fi

    echo "Rendering ConfigMap template..."
    helm template "${base_chart}" \
        --values "${base_chart}/values.yaml"${env_overrides}${app_overrides} \
        -s templates/configMap.yaml \
        --set appName="${APP_LOWER}" \
        --set includeConfigMap=true \
        --set namespace="${NAMESPACE}" \
        --set appType="FWRK-WEBAPP" \
        --debug > ./configMap.yaml

    oc project "${NAMESPACE}" --server="${OCP_URL}" --token="${OCP_TOKEN}"

    local configmap_name="${APP_LOWER}-cmp"

    if ! oc get configmap "${configmap_name}" \
            -n "${NAMESPACE}" \
            --server="${OCP_URL}" \
            --token="${OCP_TOKEN}" > /dev/null 2>&1; then
        echo "ConfigMap '${configmap_name}' not found — creating..."
        oc apply -f configMap.yaml \
            -n "${NAMESPACE}" \
            --server="${OCP_URL}" \
            --token="${OCP_TOKEN}"
    else
        echo "ConfigMap '${configmap_name}' exists — patching..."
        oc patch configmap "${configmap_name}" \
            --patch-file configMap.yaml \
            -n "${NAMESPACE}" \
            --server="${OCP_URL}" \
            --token="${OCP_TOKEN}"
    fi
}

# ── Helm values assembly ──────────────────────────────────────────────────
build_helm_values_args() {
    HELM_VALUES_ARGS=""

    # Base values
    HELM_VALUES_ARGS+=" --values ./base-chart/default/helm-chart/values.yaml"

    # Environment overrides (dev uses defaults only)
    if [ "${ENV_NAME}" != "dev" ]; then
        HELM_VALUES_ARGS+=" --values ./env-overrides/default/${ENV_NAME}/values.yaml"
    fi

    # App-specific overrides
    if [ "${OVERRIDE_YAML_EXISTS}" = "true" ]; then
        HELM_VALUES_ARGS+=" --values ./app-overrides/apps/${APP_LOWER}/${ENV_NAME}/values.yaml"
    fi

    # Keystore overrides (if present in ocpConfigDir)
    if [ -f "./values.yaml" ]; then
        echo "Keystore override values found"
        HELM_VALUES_ARGS+=" --values ./values.yaml"
    fi

    # Standard set overrides
    HELM_VALUES_ARGS+=" --set appName=\"${APP_LOWER}\""
    HELM_VALUES_ARGS+=" --set image.tag=${IMAGE_ID}"
    HELM_VALUES_ARGS+=" --set namespace=\"${NAMESPACE}\""
    HELM_VALUES_ARGS+=" --set appType=\"FWRK-WEBAPP\""

    # deploy-timestamp changes every run, forcing a pod rollout even when
    # the image tag is unchanged (e.g. after ConfigMap/Secret updates).
    HELM_VALUES_ARGS+=" --set podAnnotations.deploy-timestamp=\"${DEPLOY_TIME}\""

    HELM_VALUES_ARGS+=" --debug"
}

# ── Helm template + oc apply ──────────────────────────────────────────────
helm_template_and_apply() {
    echo "Generating deployment manifest..."
    eval helm template "${APP_NAME}" ./base-chart/default/helm-chart \
        ${HELM_VALUES_ARGS} \
        --set includeConfigmap=false \
        > ./deployment.yaml

    echo "Generated deployment manifest:"
    cat ./deployment.yaml

    echo "Applying deployment manifest..."
    oc apply -f ./deployment.yaml \
        -n "${NAMESPACE}" \
        --server="${OCP_URL}" \
        --token="${OCP_TOKEN}"
}

# ── Rollout verification ──────────────────────────────────────────────────
wait_for_rollout() {
    echo "Waiting for rollout to complete (timeout: ${ROLLOUT_TIMEOUT_SECONDS}s)..."
    oc rollout status "deploy/${APP_NAME}" \
        -n "${NAMESPACE}" \
        --server="${OCP_URL}" \
        --token="${OCP_TOKEN}" \
        --timeout="${ROLLOUT_TIMEOUT_SECONDS}s"
    echo "Rollout complete."
}

# ── Main ───────────────────────────────────────────────────────────────────
main() {
    echo "========================================"
    echo "Deploy App"
    echo "  APP_NAME  : ${APP_NAME}"
    echo "  ENV_NAME  : ${ENV_NAME}"
    echo "  IMAGE_ID  : ${IMAGE_ID}"
    echo "  NAMESPACE : ${NAMESPACE}"
    echo "  OCP_URL   : ${OCP_URL}"
    echo "========================================"

    login_to_ocp

    run_maven_plugins

    pushd "${OCP_CONFIG_DIR}" > /dev/null

    manage_configmap

    build_helm_values_args

    helm_template_and_apply

    wait_for_rollout

    popd > /dev/null

    echo "Deployment of ${APP_NAME} to ${NAMESPACE} complete."
}

main "$@"
