class FwrkDeploy {

    private static final int POST_DEPLOY_WAIT_SECONDS = 200

    // Namespaces that use a shared gateway and single-pod mode for webapps
    private static final List<String> SHARED_GATEWAY_NAMESPACES = [
        'dtf-dev-nimbus-tools',
        'dtf-prod-nimbus-toolspr'
    ]

    private final Script s

    FwrkDeploy(Script script) {
        this.s = script
    }

    /**
     * Main entry point — deploys the application to OpenShift via Helm.
     */
    void execute(Map ctx) {
        def namespace    = ctx.namespace
        def ocpUrl       = ctx.ocpUrl
        def appName      = ctx.appName
        def appLower     = ctx.appLower
        def ocpConfigDir = ctx.ocpConfigDir

        s.withCredentials([s.string(credentialsId: "${namespace}-token", variable: 'OCP_TOKEN')]) {

            loginToOcp(ocpUrl)

            runMavenPlugins(ctx)

            s.dir(ocpConfigDir) {
                manageConfigMap(ctx)

                def valuesCmd = buildHelmValuesArgs(ctx)

                handleUnmanagedDeployment(ctx, valuesCmd)

                def oldPods = captureCurrentPods(ctx)

                helmUpgrade(ctx, valuesCmd)

                runPostDeployScript(ctx)

                verifyNewPods(ctx, oldPods)
            }
        }
    }

    // -------------------------------------------------------------------------
    // OCP login
    // -------------------------------------------------------------------------

    private void loginToOcp(String ocpUrl) {
        s.sh "oc login --token=\${OCP_TOKEN} --server=${ocpUrl}"
    }

    // -------------------------------------------------------------------------
    // Maven plugin execution
    // -------------------------------------------------------------------------

    private void runMavenPlugins(Map ctx) {
        s.dir('nimbus-staging-pom') {
            s.sh """mvn -DNIMBUS_MAVEN_CODEGEN_ENV=${ctx.envUpper} \
                -DAPPLICATION=${ctx.appName} \
                -DocpToken=\${OCP_TOKEN} -DocpUrl=${ctx.ocpUrl} \
                -DimageId=${ctx.imageId} -Dnamespace=${ctx.namespace} \
                -DappType=${ctx.appType} \
                gov.nystax.nimbus:nimbus-maven-security-plugin:${ctx.securityPluginVer}:app-secrets-update"""

            s.sh """mvn -DNIMBUS_MAVEN_CODEGEN_ENV=${ctx.envUpper} \
                -DworkspaceDir=${ctx.workspace}/${ctx.ocpConfigDir} \
                -DappId=${ctx.appName} \
                -DocpToken=\${OCP_TOKEN} -DocpUrl=${ctx.ocpUrl} \
                -Dnamespace=${ctx.namespace} \
                gov.nystax.nimbus:nimbus-maven-fwrkapp-plugin:${ctx.nimbusPluginVer}:fwrk-ocp-config-update"""

            s.sh """mvn -DNIMBUS_MAVEN_CODEGEN_ENV=${ctx.envUpper} \
                -DAPPLICATION=${ctx.appName} \
                -DimageId=${ctx.imageId} -DmigRlsNmbr=${ctx.migRlsNmbr} \
                gov.nystax.nimbus:nimbus-maven-fwrkapp-plugin:${ctx.nimbusPluginVer}:build-deploy-report"""
        }
    }

    // -------------------------------------------------------------------------
    // ConfigMap management
    // -------------------------------------------------------------------------

    /**
     * Renders the ConfigMap template and applies or patches it.
     * Done separately from helm upgrade because helm requires deletion
     * permissions we don't have for first-time annotation changes.
     */
    private void manageConfigMap(Map ctx) {
        def baseChart = "./base-chart/default/helm-chart"
        def envOverrides = buildEnvOverridesFlag(ctx)
        def appOverrides = buildAppOverridesFlag(ctx)

        s.sh """helm template ${baseChart} \
            --values ${baseChart}/values.yaml${envOverrides}${appOverrides} \
            -s templates/configMap.yaml \
            --set appName="${ctx.appLower}" \
            --set includeConfigMap=true \
            --set namespace="${ctx.namespace}" \
            --set appType="${ctx.helmType}" \
            --debug > ./configMap.yaml"""

        s.sh "oc project ${ctx.namespace} --server=${ctx.ocpUrl} --token=\${OCP_TOKEN}"

        def configMapName = "${ctx.appLower}-cmp"
        int found = s.sh(
            script: "oc get configmap ${configMapName} -n ${ctx.namespace} --server=${ctx.ocpUrl} --token=\${OCP_TOKEN} > /dev/null 2>&1",
            returnStatus: true
        )

        if (found != 0) {
            s.echo "ConfigMap '${configMapName}' not found — creating..."
            s.sh "oc apply -f configMap.yaml -n ${ctx.namespace} --server=${ctx.ocpUrl} --token=\${OCP_TOKEN}"
        } else {
            s.echo "ConfigMap '${configMapName}' exists — patching..."
            s.sh "oc patch configmap ${configMapName} --patch-file configMap.yaml -n ${ctx.namespace} --server=${ctx.ocpUrl} --token=\${OCP_TOKEN}"
        }
    }

    // -------------------------------------------------------------------------
    // Helm values assembly
    // -------------------------------------------------------------------------

    /**
     * Builds the full --values / --set argument string for helm upgrade.
     */
    private String buildHelmValuesArgs(Map ctx) {
        def parts = []

        // Base values
        parts << "--values ./base-chart/default/helm-chart/values.yaml"

        // Environment overrides (dev uses defaults only)
        parts << buildEnvOverridesFlag(ctx)

        // App-specific overrides
        parts << buildAppOverridesFlag(ctx)

        // App-type-specific gateway settings
        parts << buildAppTypeOverrides(ctx)

        // Single pod for webapps in shared namespaces
        if (ctx.isWebApp && ctx.namespace in SHARED_GATEWAY_NAMESPACES) {
            parts << '--set replicaCount="1.0"'
        }

        // Keystore overrides (if present in ocpConfigDir)
        if (s.fileExists("./values.yaml")) {
            s.echo "Keystore override values found"
            parts << "--values ./values.yaml"
        }

        // Standard set overrides
        parts << "--set appName=\"${ctx.appLower}\""
        parts << "--set image.tag=${ctx.imageId}"
        parts << "--set namespace=\"${ctx.namespace}\""
        parts << "--set appType=\"${ctx.helmType}\""
        parts << "--debug"

        return parts.findAll { it }.join(' ')
    }

    private String buildEnvOverridesFlag(Map ctx) {
        if (ctx.envName != "dev") {
            return " --values ./env-overrides/default/${ctx.envName}/values.yaml"
        }
        return ""
    }

    private String buildAppOverridesFlag(Map ctx) {
        if (ctx.overrideYamlExists) {
            return " --values ./app-overrides/apps/${ctx.appLower}/${ctx.envName}/values.yaml"
        }
        return ""
    }

    private String buildAppTypeOverrides(Map ctx) {
        def useSharedGateway = ctx.isWebApp &&
                               ctx.appLower != "nimbus-config-web" &&
                               ctx.namespace in SHARED_GATEWAY_NAMESPACES

        if (useSharedGateway) {
            return "--set includeGateway=false " +
                   "--set virtualService.gateway=\"${ctx.istioGatewayName}\" " +
                   "--set virtualService.hosts=\"${ctx.istioGatewayHostName}\""
        }
        return "--set virtualService.gateway=\"${ctx.appLower}-gateway\""
    }

    // -------------------------------------------------------------------------
    // Deployment lifecycle
    // -------------------------------------------------------------------------

    /**
     * If a deployment exists but is NOT managed by Helm, delete it so
     * helm upgrade can recreate it with proper labels.
     */
    private void handleUnmanagedDeployment(Map ctx, String valuesCmd) {
        int deployExists = s.sh(
            script: "oc get deploy ${ctx.appName} -n ${ctx.namespace} --server=${ctx.ocpUrl} --token=\${OCP_TOKEN} > /dev/null 2>&1",
            returnStatus: true
        )
        if (deployExists != 0) {
            return // no existing deployment — nothing to do
        }

        int helmManaged = s.sh(
            script: "oc get deploy ${ctx.appName} -n ${ctx.namespace} -o jsonpath='{.metadata.labels.app\\.kubernetes\\.io/managed-by}' --token=\${OCP_TOKEN} --server=${ctx.ocpUrl} | grep -q Helm",
            returnStatus: true
        )
        if (helmManaged == 0) {
            return // already managed by Helm — nothing to do
        }

        s.echo "Deployment exists but is not Helm-managed — deleting for Helm takeover..."
        s.sh "helm template ./base-chart/default/helm-chart ${valuesCmd} --set includeConfigmap=false > ./manifestsToDelete.yaml"
        s.sh "oc delete -f manifestsToDelete.yaml --ignore-not-found=true"
    }

    private void helmUpgrade(Map ctx, String valuesCmd) {
        s.sh """helm upgrade ${ctx.appName} ./base-chart/default/helm-chart \
            --kube-token \${OCP_TOKEN} \
            -n ${ctx.namespace} \
            --force --install -oyaml \
            ${valuesCmd} \
            --set includeConfigmap=false \
            > ./deployment_output.yaml"""
    }

    // -------------------------------------------------------------------------
    // Post-deployment
    // -------------------------------------------------------------------------

    private List<String> captureCurrentPods(Map ctx) {
        def output = s.sh(
            script: "oc get pods -n ${ctx.namespace} --token=\${OCP_TOKEN} -l app=${ctx.appLower} --server=${ctx.ocpUrl} -o jsonpath='{.items[*].metadata.name}'",
            returnStdout: true
        ).trim()
        return output ? output.split(" ") as List : []
    }

    private void runPostDeployScript(Map ctx) {
        def imageUrl = "${ctx.registryUrl}/${ctx.imageId}"
        String script = s.libraryResource 'sh/postdeploy.sh'
        s.writeFile file: "postdeploy.sh", text: script
        s.sh "chmod 775 postdeploy.sh"

        s.echo "Waiting ${POST_DEPLOY_WAIT_SECONDS}s for pods to stabilize..."
        s.sh "sleep ${POST_DEPLOY_WAIT_SECONDS}"

        s.sh "./postdeploy.sh '${imageUrl}' '${ctx.appLower}' '${ctx.namespace}' \${OCP_TOKEN} '${ctx.ocpUrl}'"
    }

    private void verifyNewPods(Map ctx, List<String> oldPods) {
        def currentPods = captureCurrentPods(ctx)

        boolean hasNewPods = currentPods.any { !(it in oldPods) }

        if (!hasNewPods) {
            s.echo "No new pods detected — forcing rollout restart..."
            s.sh "oc rollout restart deploy ${ctx.appName} -n ${ctx.namespace} --token=\${OCP_TOKEN} --server=${ctx.ocpUrl}"

            s.echo "Waiting ${POST_DEPLOY_WAIT_SECONDS}s for restarted pods..."
            s.sh "sleep ${POST_DEPLOY_WAIT_SECONDS}"

            def imageUrl = "${ctx.registryUrl}/${ctx.imageId}"
            s.sh "./postdeploy.sh '${imageUrl}' '${ctx.appLower}' '${ctx.namespace}' \${OCP_TOKEN} '${ctx.ocpUrl}'"
        }
    }
}
