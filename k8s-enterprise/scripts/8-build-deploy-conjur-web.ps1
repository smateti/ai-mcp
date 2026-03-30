#######################################################################
# 8-build-deploy-conjur-web.ps1 — Ops: Build & Deploy Conjur Web UI
#
# Builds conjur-microprofile-web, pushes to local registry, deploys
# to K8s. This is the DBA-friendly web UI for managing Conjur
# databases, applications, and secrets via the REST API service.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$AppDir = Join-Path $ProjectDir "conjur-microprofile-web"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  OPS — Conjur Web UI Build & Deploy" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Pre-check: Verify conjur-rest is running ---
Write-Host "  Pre-check: Verifying conjur-rest API is reachable..." -ForegroundColor Yellow
try {
    $status = Invoke-RestMethod -Uri "http://localhost:30086/api/status" -Method Get -TimeoutSec 5
    Write-Host "  conjur-rest API is healthy" -ForegroundColor Green
} catch {
    Write-Host "  WARNING: conjur-rest may not be running at localhost:30086" -ForegroundColor DarkYellow
    Write-Host "  Run .\scripts\7-build-deploy-conjur-rest.ps1 first." -ForegroundColor Yellow
}
Write-Host ""

# --- Step 1: Build with Maven ---
Write-Host "[1/5] Building conjur-microprofile-web with Maven..." -ForegroundColor Yellow
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
    docker build -t localhost:30500/conjur-microprofile-web:latest .
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
docker push localhost:30500/conjur-microprofile-web:latest
Write-Host "       Image pushed to registry" -ForegroundColor Green

# --- Step 4: Deploy to K8s ---
Write-Host "[4/5] Deploying conjur-web to K8s (apps namespace)..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/apps/conjur-microprofile-web.yaml")

Write-Host "       Waiting for conjur-web pod to be ready..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=conjur-web -n apps --timeout=180s
Write-Host "       conjur-web is running" -ForegroundColor Green

# --- Step 5: Verify ---
Write-Host "[5/5] Running smoke tests..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

try {
    $response = Invoke-WebRequest -Uri "http://localhost:30087/" -Method Get -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        Write-Host "       GET / — Dashboard loaded (HTTP 200)" -ForegroundColor Green
    }
} catch {
    Write-Host "       WARNING: Web UI may need a moment to start" -ForegroundColor DarkYellow
    Write-Host "       Try: http://localhost:30087/" -ForegroundColor Gray
}

try {
    $health = Invoke-RestMethod -Uri "http://localhost:30087/health" -Method Get
    Write-Host "       Health status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "       Health check pending..." -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Conjur Web UI deployment COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Image:     localhost:30500/conjur-microprofile-web:latest"
Write-Host "  Pod:       conjur-web (apps namespace)"
Write-Host ""
Write-Host "  Pages:" -ForegroundColor White
Write-Host "    Dashboard:  http://localhost:30087/" -ForegroundColor Cyan
Write-Host "    Databases:  http://localhost:30087/databases" -ForegroundColor Cyan
Write-Host "    Apps:       http://localhost:30087/apps" -ForegroundColor Cyan
Write-Host "    Secrets:    http://localhost:30087/secrets" -ForegroundColor Cyan
Write-Host "    Health:     http://localhost:30087/health" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Architecture:" -ForegroundColor White
Write-Host "    Browser -> conjur-web (:30087) -> conjur-rest (:30086) -> Conjur OSS (:30080)"
Write-Host ""
