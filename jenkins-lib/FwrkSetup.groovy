class FwrkSetup {

    private static final String BASE_GIT_URL        = "git@nyssc.svc.ny.gov:DTF/framework/"
    private static final String STAGING_GIT_SUFFIX  = "nimbus/nimbus-fwrkapp-staging.git"
    private static final String HELM_GIT_SUFFIX     = "nimbus/devops/nimbus-devops-fwrk-overrides"
    private static final String GIT_BRANCH          = "main"
    private static final String HELM_DEFAULTS_PATH  = "/default/"
    private static final String HELM_APPS_PATH      = "/apps/"
    private static final String GIT_CREDENTIAL_ID   = "srvdtf_srv_git"
    private static final int    CLONE_TIMEOUT       = 240

    private final Script s

    FwrkSetup(Script script) {
        this.s = script
    }

    /**
     * Sets up the workspace: clones staging repo, sparse-checkouts helm overrides.
     */
    void execute(Map ctx) {
        def appLower     = ctx.appLower
        def envName      = ctx.envName
        def ocpConfigDir = ctx.ocpConfigDir

        // 1. Clone staging repo and flatten it into workspace root
        cloneRepo(BASE_GIT_URL + STAGING_GIT_SUFFIX, GIT_BRANCH, "staging")
        flattenDirectory("staging")

        // 2. Sparse-checkout helm chart layers
        def helmRepo = BASE_GIT_URL + HELM_GIT_SUFFIX

        sparseCheckout(helmRepo, GIT_BRANCH, "${HELM_DEFAULTS_PATH}helm-chart",
                       ocpConfigDir, "base-chart")

        sparseCheckout(helmRepo, GIT_BRANCH, "${HELM_DEFAULTS_PATH}${envName}",
                       ocpConfigDir, "env-overrides")

        sparseCheckout(helmRepo, GIT_BRANCH, "${HELM_APPS_PATH}${appLower}/${envName}",
                       ocpConfigDir, "app-overrides")
    }

    // -------------------------------------------------------------------------
    // Git operations
    // -------------------------------------------------------------------------

    private void cloneRepo(String url, String branch, String targetDir) {
        s.sh "mkdir -p ${targetDir}"
        s.dir(targetDir) {
            s.checkout(
                changelog: false,
                poll: false,
                scm: [
                    $class: 'GitSCM',
                    branches: [[name: branch]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [[$class: 'CloneOption', timeout: CLONE_TIMEOUT]],
                    gitTool: 'git',
                    submoduleCfg: [],
                    userRemoteConfigs: [[
                        credentialsId: GIT_CREDENTIAL_ID,
                        url: url
                    ]]
                ]
            )
        }
    }

    private void sparseCheckout(String url, String branch, String path,
                                String rootDir, String targetDir) {
        s.sh "mkdir -p ${rootDir}/${targetDir}"
        s.dir("${rootDir}/${targetDir}") {
            s.checkout(
                changelog: false,
                poll: false,
                scm: [
                    $class: 'GitSCM',
                    branches: [[name: branch]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [
                        [$class: 'CloneOption', timeout: CLONE_TIMEOUT],
                        [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [
                            [$class: 'SparseCheckoutPath', path: path]
                        ]]
                    ],
                    gitTool: 'git',
                    submoduleCfg: [],
                    userRemoteConfigs: [[
                        credentialsId: GIT_CREDENTIAL_ID,
                        url: url
                    ]]
                ]
            )
        }
    }

    // -------------------------------------------------------------------------
    // Filesystem helpers
    // -------------------------------------------------------------------------

    private void flattenDirectory(String directory) {
        s.dir(directory) {
            s.sh "cp -r ./* ../"
        }
        s.sh "rm -rf ${directory}"
    }
}
