#######################################################################
# 11-generate-app-yaml.ps1 — Generate K8s deployment YAML for an app
#
# Creates a deployment manifest from a template with correct Conjur
# integration (per-app K8s Secret, ConfigMap, CONJUR_SECRETS mapping).
#######################################################################

param(
    [Parameter(Mandatory=$true)]
    [string]$AppName,

    [Parameter(Mandatory=$false)]
    [string]$Namespace = "apps",

    [Parameter(Mandatory=$false)]
    [int]$NodePort = 0,

    [Parameter(Mandatory=$false)]
    [string]$Databases = "",

    [Parameter(Mandatory=$false)]
    [string]$ExtraSecrets = "",

    [Parameter(Mandatory=$false)]
    [string]$Image = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  OPS — Generate K8s YAML for $AppName" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

if (-not $Image) { $Image = "localhost:30500/${AppName}:latest" }

# Build CONJUR_SECRETS mapping
$secretsMapping = ""
if ($Databases) {
    foreach ($db in ($Databases -split ",")) {
        $db = $db.Trim()
        if ($secretsMapping) { $secretsMapping += "," }
        $secretsMapping += "db.username=db/${db}-uid,db.password=db/${db}-pwd"
    }
}
if ($ExtraSecrets) {
    foreach ($pair in ($ExtraSecrets -split ",")) {
        if ($secretsMapping) { $secretsMapping += "," }
        $secretsMapping += $pair.Trim()
    }
}

# Build ConfigMap env vars for first database
$configMapEnv = ""
if ($Databases) {
    $firstDb = ($Databases -split ",")[0].Trim()
    $configMapName = "${firstDb}-config"
    $configMapEnv = @"
        - name: DB_HOST
          valueFrom:
            configMapKeyRef:
              name: $configMapName
              key: db.host
              optional: true
        - name: DB_PORT
          valueFrom:
            configMapKeyRef:
              name: $configMapName
              key: db.port
              optional: true
        - name: DB_NAME
          valueFrom:
            configMapKeyRef:
              name: $configMapName
              key: db.name
              optional: true
"@
}

# Service section (optional if NodePort specified)
$serviceYaml = ""
if ($NodePort -gt 0) {
    $serviceYaml = @"
---
apiVersion: v1
kind: Service
metadata:
  name: $AppName
  namespace: $Namespace
spec:
  type: NodePort
  selector:
    app: $AppName
  ports:
  - port: 9080
    targetPort: 9080
    nodePort: $NodePort
"@
}

$yaml = @"
apiVersion: apps/v1
kind: Deployment
metadata:
  name: $AppName
  namespace: $Namespace
  labels:
    app: $AppName
spec:
  replicas: 1
  selector:
    matchLabels:
      app: $AppName
  template:
    metadata:
      labels:
        app: $AppName
    spec:
      containers:
      - name: $AppName
        image: $Image
        ports:
        - containerPort: 9080
        env:
        # Per-app Conjur identity (from K8s Secret conjur-$AppName)
        - name: CONJUR_APPLIANCE_URL
          valueFrom:
            secretKeyRef:
              name: conjur-$AppName
              key: CONJUR_APPLIANCE_URL
        - name: CONJUR_ACCOUNT
          valueFrom:
            secretKeyRef:
              name: conjur-$AppName
              key: CONJUR_ACCOUNT
        - name: CONJUR_AUTHN_LOGIN
          valueFrom:
            secretKeyRef:
              name: conjur-$AppName
              key: CONJUR_AUTHN_LOGIN
        - name: CONJUR_AUTHN_API_KEY
          valueFrom:
            secretKeyRef:
              name: conjur-$AppName
              key: CONJUR_AUTHN_API_KEY
        # Conjur secret mappings (used by conjur-microprofile-lib)
        - name: CONJUR_SECRETS
          value: "$secretsMapping"
$configMapEnv
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 9080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /health/live
            port: 9080
          initialDelaySeconds: 45
          periodSeconds: 15
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
$serviceYaml
"@

# Write output
$outputDir = Join-Path $ProjectDir "k8s/apps"
$outputFile = Join-Path $outputDir "${AppName}.yaml"
$yaml | Out-File -FilePath $outputFile -Encoding UTF8

Write-Host "  Generated: $outputFile" -ForegroundColor Green
Write-Host ""
Write-Host "  Configuration:" -ForegroundColor White
Write-Host "    App:          $AppName"
Write-Host "    Namespace:    $Namespace"
Write-Host "    Image:        $Image"
Write-Host "    K8s Secret:   conjur-$AppName"
Write-Host "    CONJUR_SECRETS: $secretsMapping" -ForegroundColor Gray
if ($NodePort -gt 0) { Write-Host "    NodePort:     $NodePort" }
Write-Host ""
Write-Host "  Deploy with:" -ForegroundColor Cyan
Write-Host "    kubectl apply -f $outputFile" -ForegroundColor Cyan
