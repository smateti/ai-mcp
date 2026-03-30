#######################################################################
# 0-prereqs-check.ps1 — Verify Prerequisites
#
# Checks that all required tools are installed and Docker Desktop
# Kubernetes is running before proceeding with the setup.
#######################################################################

$ErrorActionPreference = "Continue"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  PREREQUISITES CHECK" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

$allGood = $true

function Check-Tool {
    param([string]$Name, [string]$Command, [string]$MinVersion)
    try {
        $output = Invoke-Expression $Command 2>&1
        Write-Host "  [OK] $Name" -ForegroundColor Green
        Write-Host "       $($output | Select-Object -First 1)" -ForegroundColor Gray
    } catch {
        Write-Host "  [FAIL] $Name — not found!" -ForegroundColor Red
        $script:allGood = $false
    }
}

# Docker
Check-Tool "Docker" "docker version --format '{{.Client.Version}}'"
# kubectl
Check-Tool "kubectl" "kubectl version --client 2>&1"
# Helm
Check-Tool "Helm" "helm version --short"
# Java
Check-Tool "Java 17+" "java -version 2>&1"
# Maven
Check-Tool "Maven" "mvn -version 2>&1 | Select-Object -First 1"

Write-Host ""

# Check Kubernetes cluster
Write-Host "  Checking Kubernetes cluster..." -ForegroundColor Yellow
try {
    $clusterInfo = kubectl cluster-info 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Kubernetes cluster is running" -ForegroundColor Green
        $nodes = kubectl get nodes --no-headers 2>&1
        Write-Host "       $nodes" -ForegroundColor Gray
    } else {
        Write-Host "  [FAIL] Kubernetes cluster not reachable" -ForegroundColor Red
        Write-Host "       Enable Kubernetes in Docker Desktop Settings" -ForegroundColor Yellow
        $allGood = $false
    }
} catch {
    Write-Host "  [FAIL] Cannot connect to Kubernetes" -ForegroundColor Red
    $allGood = $false
}

# Check Docker memory
Write-Host ""
Write-Host "  Checking Docker memory allocation..." -ForegroundColor Yellow
try {
    $memInfo = docker info 2>&1 | Select-String "Total Memory"
    if ($memInfo) {
        Write-Host "  [INFO] $($memInfo.ToString().Trim())" -ForegroundColor Gray
        Write-Host "         Recommended: 8GB+ for this setup" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [WARN] Could not check Docker memory" -ForegroundColor DarkYellow
}

Write-Host ""
if ($allGood) {
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  All prerequisites MET" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  Next: Run .\scripts\1-setup-registry.ps1" -ForegroundColor Cyan
} else {
    Write-Host "============================================" -ForegroundColor Red
    Write-Host "  Some prerequisites MISSING" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    Write-Host "  Fix the issues above before continuing." -ForegroundColor Yellow
    exit 1
}
