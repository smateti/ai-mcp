#######################################################################
# 7-build-deploy-conjur-rest.ps1 — Ops: Build & Deploy Conjur REST API
#
# Builds conjur-microprofile-rest, pushes to local registry, deploys
# to K8s. This is the headless REST API service for managing Conjur
# policies, secrets, and application identities.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$AppDir = Join-Path $ProjectDir "conjur-microprofile-rest"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  OPS — Conjur REST API Build & Deploy" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Pre-check: Verify Conjur is running ---
Write-Host "  Pre-check: Verifying Conjur is reachable..." -ForegroundColor Yellow
try {
    $conjurHealth = Invoke-RestMethod -Uri "http://localhost:30080/health" -Method Get -TimeoutSec 5
    Write-Host "  Conjur is healthy" -ForegroundColor Green
} catch {
    Write-Host "  WARNING: Conjur may not be reachable at localhost:30080" -ForegroundColor DarkYellow
    Write-Host "  The REST API will retry connections on startup." -ForegroundColor Gray
}
Write-Host ""

# --- Step 1: Build with Maven ---
Write-Host "[1/5] Building conjur-microprofile-rest with Maven..." -ForegroundColor Yellow
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
    docker build -t localhost:30500/conjur-microprofile-rest:latest .
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
docker push localhost:30500/conjur-microprofile-rest:latest
Write-Host "       Image pushed to registry" -ForegroundColor Green

# --- Step 4: Deploy to K8s ---
Write-Host "[4/5] Deploying conjur-rest to K8s (apps namespace)..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/apps/conjur-microprofile-rest.yaml")

Write-Host "       Waiting for conjur-rest pod to be ready..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=conjur-rest -n apps --timeout=180s
Write-Host "       conjur-rest is running" -ForegroundColor Green

# --- Step 5: Verify ---
Write-Host "[5/5] Running smoke tests..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

try {
    $status = Invoke-RestMethod -Uri "http://localhost:30086/api/status" -Method Get
    Write-Host "       GET /api/status — connected: $($status.conjurReachable)" -ForegroundColor Green
} catch {
    Write-Host "       WARNING: API may need a moment to start" -ForegroundColor DarkYellow
    Write-Host "       Try: curl http://localhost:30086/api/status" -ForegroundColor Gray
}

try {
    $health = Invoke-RestMethod -Uri "http://localhost:30086/health" -Method Get
    Write-Host "       Health status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "       Health check pending..." -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Conjur REST API deployment COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Image:     localhost:30500/conjur-microprofile-rest:latest"
Write-Host "  Pod:       conjur-rest (apps namespace)"
Write-Host ""
Write-Host "  Endpoints:" -ForegroundColor White
Write-Host "    Status:  http://localhost:30086/api/status" -ForegroundColor Cyan
Write-Host "    Secrets: http://localhost:30086/api/secrets" -ForegroundColor Cyan
Write-Host "    Policies:http://localhost:30086/api/policies" -ForegroundColor Cyan
Write-Host "    Health:  http://localhost:30086/health" -ForegroundColor Cyan
Write-Host "    OpenAPI: http://localhost:30086/openapi/ui/" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Next: Run .\scripts\8-build-deploy-conjur-web.ps1" -ForegroundColor Cyan
