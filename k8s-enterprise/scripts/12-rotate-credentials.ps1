#######################################################################
# 12-rotate-credentials.ps1 — Rotate database credentials in Conjur
#
# Updates DB credentials in Conjur vault and performs a rolling restart
# of all pods that consume those credentials.
#######################################################################

param(
    [Parameter(Mandatory=$true)]
    [string]$Database,

    [Parameter(Mandatory=$true)]
    [string]$Username,

    [Parameter(Mandatory=$true)]
    [string]$Password,

    [Parameter(Mandatory=$false)]
    [string]$Namespace = "apps",

    [Parameter(Mandatory=$false)]
    [string]$RestApiUrl = "http://localhost:30086"
)

$ErrorActionPreference = "Stop"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  OPS — Rotate Credentials for $Database" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Pre-check ---
Write-Host "  Pre-check: Verifying conjur-rest API..." -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$RestApiUrl/api/status" -Method Get -TimeoutSec 5 | Out-Null
    Write-Host "  API reachable" -ForegroundColor Green
} catch {
    Write-Host "  ERROR: conjur-rest not reachable" -ForegroundColor Red
    exit 1
}
Write-Host ""

# --- Step 1: Update credentials in Conjur ---
Write-Host "[1/3] Updating credentials in Conjur vault..." -ForegroundColor Yellow
$body = @{ username = $Username; password = $Password } | ConvertTo-Json
try {
    Invoke-RestMethod -Uri "$RestApiUrl/api/secrets/db/$Database" `
        -Method Put -Body $body -ContentType "application/json" -TimeoutSec 10
    Write-Host "       Credentials updated for '$Database'" -ForegroundColor Green
} catch {
    Write-Host "       ERROR: Failed to update credentials" -ForegroundColor Red
    Write-Host "       $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# --- Step 2: Verify update ---
Write-Host "[2/3] Verifying updated credentials..." -ForegroundColor Yellow
try {
    $creds = Invoke-RestMethod -Uri "$RestApiUrl/api/secrets/db/$Database" -Method Get -TimeoutSec 10
    if ($creds.username -eq $Username) {
        Write-Host "       Verified: username=$($creds.username)" -ForegroundColor Green
    } else {
        Write-Host "       WARNING: username mismatch" -ForegroundColor DarkYellow
    }
} catch {
    Write-Host "       WARNING: Could not verify" -ForegroundColor DarkYellow
}

# --- Step 3: Rolling restart ---
Write-Host "[3/3] Rolling restart of pods in '$Namespace' namespace..." -ForegroundColor Yellow

# Get all deployments in namespace
$deployments = kubectl get deployments -n $Namespace -o jsonpath="{.items[*].metadata.name}" 2>$null
if ($deployments) {
    foreach ($dep in ($deployments -split " ")) {
        # Check if deployment uses the database's ConfigMap or Secret
        $depYaml = kubectl get deployment $dep -n $Namespace -o yaml 2>$null
        if ($depYaml -match $Database -or $depYaml -match "conjur-") {
            Write-Host "       Restarting: $dep" -ForegroundColor Gray
            kubectl rollout restart deployment/$dep -n $Namespace 2>$null
        }
    }
    Write-Host "       Rolling restarts initiated" -ForegroundColor Green
} else {
    Write-Host "       No deployments found in '$Namespace'" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Credential Rotation COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Database:     $Database"
Write-Host "  Username:     $Username"
Write-Host "  Conjur vars:  db/${Database}-uid, db/${Database}-pwd"
Write-Host ""
Write-Host "  Pods will pick up new credentials as they restart." -ForegroundColor Cyan
Write-Host "  Apps using conjur-microprofile-lib will auto-refresh on next startup." -ForegroundColor Cyan
