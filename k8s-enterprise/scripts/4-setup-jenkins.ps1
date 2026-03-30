#######################################################################
# 4-setup-jenkins.ps1 — CI/CD Team: Jenkins on Kubernetes (Helm)
#
# Installs Jenkins via Helm chart with K8s plugin for dynamic agents.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  CI/CD TEAM — Jenkins Setup on K8s" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Create RBAC ---
Write-Host "[1/4] Creating Jenkins ServiceAccount and RBAC..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/jenkins/rbac.yaml")
Write-Host "       ServiceAccount + ClusterRole created" -ForegroundColor Green

# --- Step 2: Add Helm repo ---
Write-Host "[2/4] Adding Jenkins Helm repository..." -ForegroundColor Yellow
helm repo add jenkinsci https://charts.jenkins.io 2>$null
helm repo update
Write-Host "       Helm repo updated" -ForegroundColor Green

# --- Step 3: Install Jenkins ---
Write-Host "[3/4] Installing Jenkins via Helm (this takes 3-5 minutes)..." -ForegroundColor Yellow
$valuesFile = Join-Path $ProjectDir "k8s/jenkins/values.yaml"

# Check if already installed
$existing = helm list -n jenkins --filter jenkins -q 2>$null
if ($existing -eq "jenkins") {
    Write-Host "       Jenkins already installed — upgrading..." -ForegroundColor DarkYellow
    helm upgrade jenkins jenkinsci/jenkins -n jenkins -f $valuesFile --wait --timeout 10m
} else {
    helm install jenkins jenkinsci/jenkins -n jenkins -f $valuesFile --wait --timeout 10m
}
Write-Host "       Jenkins installed successfully" -ForegroundColor Green

# --- Step 4: Get admin password ---
Write-Host "[4/4] Retrieving Jenkins admin credentials..." -ForegroundColor Yellow
$jenkinsPod = kubectl get pod -l app.kubernetes.io/name=jenkins -n jenkins -o jsonpath="{.items[0].metadata.name}"
$adminPassword = kubectl exec $jenkinsPod -n jenkins -- cat /run/secrets/additional/chart-admin-password 2>$null
if (-not $adminPassword) {
    $adminPassword = kubectl get secret jenkins -n jenkins -o jsonpath="{.data.jenkins-admin-password}" 2>$null
    if ($adminPassword) {
        $adminPassword = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($adminPassword))
    }
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Jenkins setup COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  URL:      http://localhost:30880" -ForegroundColor Cyan
Write-Host "  Username: admin"
Write-Host "  Password: $adminPassword"
Write-Host ""
Write-Host "  Jenkins is configured with:" -ForegroundColor White
Write-Host "    - Kubernetes cloud (dynamic agents)"
Write-Host "    - Maven agent template (maven:3.9 + docker cli)"
Write-Host "    - Pipeline + Git plugins"
Write-Host ""
Write-Host "  To create a pipeline:" -ForegroundColor Yellow
Write-Host "    1. Open http://localhost:30880"
Write-Host "    2. New Item > Pipeline"
Write-Host "    3. Point to the Jenkinsfile in the employee-app folder"
Write-Host ""
Write-Host "  Next: Run .\scripts\5-build-deploy-propsetapp.ps1" -ForegroundColor Cyan
