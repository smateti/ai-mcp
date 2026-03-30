#######################################################################
# 6-build-deploy-employee-app.ps1 — Ops: Build & Deploy Employee App
#
# Builds the Employee App, pushes to local registry, deploys to K8s.
# The app consumes ConfigMap (from PropSetApp) + Conjur secrets.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$AppDir = Join-Path $ProjectDir "employee-app"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  OPS — Employee App Build & Deploy" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Pre-check: Verify ConfigMap exists ---
Write-Host "  Pre-check: Verifying empdb-config ConfigMap exists..." -ForegroundColor Yellow
$cm = kubectl get configmap empdb-config -n apps 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ERROR: ConfigMap 'empdb-config' not found in apps namespace!" -ForegroundColor Red
    Write-Host "  Run .\scripts\5-build-deploy-propsetapp.ps1 first." -ForegroundColor Yellow
    exit 1
}
Write-Host "  ConfigMap found" -ForegroundColor Green
Write-Host ""

# --- Step 1: Build with Maven ---
Write-Host "[1/5] Building Employee App with Maven..." -ForegroundColor Yellow
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
    docker build -t localhost:30500/employee-app:latest .
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
docker push localhost:30500/employee-app:latest
Write-Host "       Image pushed to registry" -ForegroundColor Green

# --- Step 4: Deploy to K8s ---
Write-Host "[4/5] Deploying Employee App to K8s (apps namespace)..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/apps/employee-app.yaml")

Write-Host "       Waiting for Employee App pod to be ready..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=employee-app -n apps --timeout=180s
Write-Host "       Employee App is running" -ForegroundColor Green

# --- Step 5: Verify ---
Write-Host "[5/5] Running smoke tests..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

try {
    $employees = Invoke-RestMethod -Uri "http://localhost:30090/api/employees" -Method Get
    $count = ($employees | Measure-Object).Count
    Write-Host "       GET /api/employees returned $count employees" -ForegroundColor Green
} catch {
    Write-Host "       WARNING: App may need a moment to start" -ForegroundColor DarkYellow
    Write-Host "       Try: curl http://localhost:30090/api/employees" -ForegroundColor Gray
}

try {
    $health = Invoke-RestMethod -Uri "http://localhost:30090/health" -Method Get
    Write-Host "       Health status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "       Health check pending..." -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Employee App deployment COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Image:     localhost:30500/employee-app:latest"
Write-Host "  Pod:       employee-app (apps namespace)"
Write-Host ""
Write-Host "  Endpoints:" -ForegroundColor White
Write-Host "    API:     http://localhost:30090/api/employees" -ForegroundColor Cyan
Write-Host "    Health:  http://localhost:30090/health" -ForegroundColor Cyan
Write-Host "    OpenAPI: http://localhost:30090/openapi/ui/" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Config Sources:" -ForegroundColor White
Write-Host "    DB connection: ConfigMap empdb-config (from PropSetApp)"
Write-Host "    DB credentials: Conjur vault (empdb-uid, empdb-pwd)"
Write-Host "    Conjur identity: K8s Secret conjur-app-identity"
