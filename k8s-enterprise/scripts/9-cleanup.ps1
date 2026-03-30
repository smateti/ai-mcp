#######################################################################
# 9-cleanup.ps1 — Teardown All Resources
#
# Removes all K8s resources, Helm releases, and local Docker images.
#######################################################################

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  CLEANUP — Teardown All K8s Resources" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Apps ---
Write-Host "Removing app deployments..." -ForegroundColor Yellow
kubectl delete -f (Join-Path $ProjectDir "k8s/apps/employee-app.yaml") --ignore-not-found 2>$null
kubectl delete -f (Join-Path $ProjectDir "k8s/apps/propsetapp.yaml") --ignore-not-found 2>$null
kubectl delete configmap empdb-config -n apps --ignore-not-found 2>$null
kubectl delete secret conjur-app-identity -n apps --ignore-not-found 2>$null

# --- Jenkins ---
Write-Host "Uninstalling Jenkins..." -ForegroundColor Yellow
helm uninstall jenkins -n jenkins 2>$null
kubectl delete -f (Join-Path $ProjectDir "k8s/jenkins/rbac.yaml") --ignore-not-found 2>$null

# --- DB2 ---
Write-Host "Removing DB2..." -ForegroundColor Yellow
kubectl delete -f (Join-Path $ProjectDir "k8s/databases/db2.yaml") --ignore-not-found 2>$null

# --- Conjur ---
Write-Host "Removing Conjur + PostgreSQL..." -ForegroundColor Yellow
kubectl delete -f (Join-Path $ProjectDir "k8s/conjur/conjur.yaml") --ignore-not-found 2>$null
kubectl delete -f (Join-Path $ProjectDir "k8s/conjur/postgres.yaml") --ignore-not-found 2>$null

# --- Registry ---
Write-Host "Removing container registry..." -ForegroundColor Yellow
kubectl delete -f (Join-Path $ProjectDir "k8s/registry/registry.yaml") --ignore-not-found 2>$null

# --- Namespaces ---
Write-Host "Removing namespaces..." -ForegroundColor Yellow
kubectl delete namespace conjur-system --ignore-not-found 2>$null
kubectl delete namespace databases --ignore-not-found 2>$null
kubectl delete namespace jenkins --ignore-not-found 2>$null
kubectl delete namespace apps --ignore-not-found 2>$null

# --- Local Docker images ---
Write-Host "Cleaning local Docker images..." -ForegroundColor Yellow
docker rmi localhost:30500/employee-app:latest 2>$null
docker rmi localhost:30500/propsetapp:latest 2>$null

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Cleanup COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  All K8s resources removed."
Write-Host "  All namespaces deleted."
Write-Host "  Local Docker images cleaned."
