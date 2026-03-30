#######################################################################
# 5-build-deploy-propsetapp.ps1 — Ops: Build & Deploy PropSetApp
#
# Builds PropSetApp, pushes to local registry, deploys to K8s.
# PropSetApp reads datasources.yaml and creates ConfigMaps.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$AppDir = Join-Path $ProjectDir "propsetapp"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  OPS — PropSetApp Build & Deploy" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Build with Maven ---
Write-Host "[1/5] Building PropSetApp with Maven..." -ForegroundColor Yellow
Push-Location $AppDir
try {
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "BUILD FAILED!" -ForegroundColor Red
        exit 1
    }
    Write-Host "       Build SUCCESS" -ForegroundColor Green
} finally {
    Pop-Location
}

# --- Step 2: Docker build ---
Write-Host "[2/5] Building Docker image..." -ForegroundColor Yellow
Push-Location $AppDir
try {
    docker build -t localhost:30500/propsetapp:latest .
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Docker build FAILED!" -ForegroundColor Red
        exit 1
    }
    Write-Host "       Docker image built" -ForegroundColor Green
} finally {
    Pop-Location
}

# --- Step 3: Push to registry ---
Write-Host "[3/5] Pushing to local registry (localhost:30500)..." -ForegroundColor Yellow
docker push localhost:30500/propsetapp:latest
Write-Host "       Image pushed to registry" -ForegroundColor Green

# --- Step 4: Deploy to K8s ---
Write-Host "[4/5] Deploying PropSetApp to K8s (apps namespace)..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/apps/propsetapp.yaml")

Write-Host "       Waiting for PropSetApp pod to be ready..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=propsetapp -n apps --timeout=180s
Write-Host "       PropSetApp is running" -ForegroundColor Green

# --- Step 5: Verify ConfigMap ---
Write-Host "[5/5] Verifying ConfigMap was published..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

$configMap = kubectl get configmap empdb-config -n apps -o yaml 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "       ConfigMap 'empdb-config' found:" -ForegroundColor Green
    kubectl get configmap empdb-config -n apps -o jsonpath="{.data}" | Write-Host -ForegroundColor Gray
} else {
    Write-Host "       WARNING: ConfigMap not yet created. Check PropSetApp logs:" -ForegroundColor DarkYellow
    Write-Host "       kubectl logs -l app=propsetapp -n apps" -ForegroundColor Gray
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  PropSetApp deployment COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Image:     localhost:30500/propsetapp:latest"
Write-Host "  Pod:       propsetapp (apps namespace)"
Write-Host "  Service:   http://localhost:30085/propset/status" -ForegroundColor Cyan
Write-Host "  ConfigMap: empdb-config (apps namespace)"
Write-Host ""
Write-Host "  ConfigMap contents:" -ForegroundColor White
Write-Host "    db.host:          db2.databases.svc.cluster.local"
Write-Host "    db.port:          50000"
Write-Host "    db.name:          empdb"
Write-Host "    db.conjur.uid.key: db/empdb-uid"
Write-Host "    db.conjur.pwd.key: db/empdb-pwd"
Write-Host ""
Write-Host "  Next: Run .\scripts\6-build-deploy-employee-app.ps1" -ForegroundColor Cyan
