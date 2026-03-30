#######################################################################
# 1-setup-registry.ps1 — Create Namespaces + Local Container Registry
#
# Creates Kubernetes namespaces and deploys registry:2 at localhost:30500
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  SETUP — Namespaces + Container Registry" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Create namespaces ---
Write-Host "[1/3] Creating Kubernetes namespaces..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/namespaces.yaml")
Write-Host "       Namespaces created: conjur-system, databases, jenkins, apps" -ForegroundColor Green

# --- Step 2: Deploy container registry ---
Write-Host "[2/3] Deploying container registry (registry:2)..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/registry/registry.yaml")

# Wait for registry pod
Write-Host "       Waiting for registry pod to be ready..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=registry -n default --timeout=120s
Write-Host "       Registry pod is ready" -ForegroundColor Green

# --- Step 3: Verify ---
Write-Host "[3/3] Verifying registry at localhost:30500..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

try {
    $catalog = Invoke-RestMethod -Uri "http://localhost:30500/v2/_catalog" -Method Get
    Write-Host "       Registry is accessible: $($catalog | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "       WARNING: Registry may need a moment to start" -ForegroundColor DarkYellow
    Write-Host "       Try: curl http://localhost:30500/v2/_catalog" -ForegroundColor Gray
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Registry setup COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Namespaces: conjur-system, databases, jenkins, apps"
Write-Host "  Registry:   localhost:30500"
Write-Host ""
Write-Host "  Next: Run .\scripts\2-setup-conjur.ps1" -ForegroundColor Cyan
