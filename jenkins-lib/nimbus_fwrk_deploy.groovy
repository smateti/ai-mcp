def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // ---------------------------------------------------------------------------
    // Build the context map — single source of truth passed to Setup and Deploy
    // ---------------------------------------------------------------------------
    def appName  = config.appName
    def envName  = config.envName
    def imageId  = params.IMAGE_ID

    def ctx = [
        appName:    appName,
        appLower:   appName.toLowerCase(),
        envName:    envName,
        envUpper:   envName.toUpperCase(),
        imageId:    imageId,
        deployTime: new Date().format('yyyyMMddHHmm'),
        migRlsNmbr: params.MIG_RLS_NMBR,

        // Set by 'Load Properties' stage
        nimbusPluginVer:   null,
        securityPluginVer: null,
        commonPluginVer:   null,
        registryUrl:       null,
        ocpUrl:            null,
        istioGatewayName:  null,
        istioGatewayHostName: null,

        // Set by 'Setup Workspace' stage
        appType:            'FWRK-SERVICE',
        isWebApp:           false,
        implType:           null,
        namespace:          null,
        helmType:           '',
        overrideYamlExists: false,
        ocpConfigDir:       "${appName.toLowerCase()}-ocp-config",
    ]

    echo "config=${config}"
    echo "params=${params}"

    pipeline {
        agent {
            node { label 'master' }
        }
        tools {
            jdk   'jdk17'
            maven 'maven'
        }
        environment {
            OCP_TOKEN = ""
        }
        stages {

            stage('Clean Workspace') {
                steps {
                    cleanWs()
                }
            }

            stage('Load Properties') {
                steps {
                    script {
                        def props = readProperties text: libraryResource('config/nimbus.properties')
                        echo "Jenkins properties = ${props}"

                        ctx.nimbusPluginVer   = props["${envName}-nimbus-maven-fwrkapp-plugin-ver"]
                        ctx.securityPluginVer = props["${envName}-nimbus-maven-security-plugin-ver"]
                        ctx.commonPluginVer   = props["${envName}-nimbus-devops-common-plugin-ver"]
                        ctx.registryUrl       = props['artifact-url']
                        ctx.ocpUrl            = props["${envName}-ocp-url"]

                        def gatewaySuffix        = props["${envName}-gateway-host-suffix"]
                        ctx.istioGatewayHostName = "${ctx.appLower}-${gatewaySuffix}"
                        ctx.istioGatewayName     = props["${envName}-istio-gateway-name"] ?: "${ctx.appLower}-gateway"
                    }
                }
            }

            stage('Setup Workspace') {
                steps {
                    script {
                        new FwrkSetup(this).execute(ctx)

                        dir('nimbus-staging-pom') {
                            sh "mvn -DNIMBUS_MAVEN_CODEGEN_ENV=${ctx.envUpper} -DappId=${ctx.appName} -DimageId=${ctx.imageId} gov.nystax.nimbus:nimbus-devops-common-plugin:${ctx.commonPluginVer}:check_image_id_validity"
                            sh "mvn -DNIMBUS_MAVEN_CODEGEN_ENV=${ctx.envUpper} -DAPPLICATION=${ctx.appName} gov.nystax.nimbus:nimbus-maven-fwrkapp-plugin:${ctx.nimbusPluginVer}:fwrk-app-config-mojo"

                            def appConfig = readProperties file: 'appConfig.properties'
                            echo "appConfig = ${appConfig}"

                            ctx.appType  = appConfig['appType']
                            ctx.isWebApp = appConfig['isWebApp'].toBoolean()
                            ctx.implType = appConfig['implType']
                            ctx.namespace = appConfig['namespace']

                            ctx.helmType = resolveHelmType(ctx.appType, ctx.implType)
                        }

                        ctx.overrideYamlExists = fileExists("${ctx.ocpConfigDir}/app-overrides/apps/${ctx.appLower}/${ctx.envName}/values.yaml")
                    }
                }
            }

            stage('Deploy App') {
                steps {
                    script {
                        ctx.workspace = env.WORKSPACE
                        buildName "${ctx.imageId}"

                        new FwrkDeploy(this).execute(ctx)

                        buildName "${ctx.appLower}:${ctx.deployTime}"
                    }
                }
            }
        }
    }
}

/**
 * Maps appType + implType to the helmType identifier used in Helm values.
 */
private String resolveHelmType(String appType, String implType) {
    if (appType == 'FWRK-WEBAPP') {
        return 'FWRK-WEBAPP'
    }
    if (appType == 'FWRK-SERVICE') {
        switch (implType) {
            case 'MicroProfile': return 'MPROFILE-SRVC'
            case 'SpringBoot':   return 'SPRING-SRVC'
        }
    }
    return ''
}
