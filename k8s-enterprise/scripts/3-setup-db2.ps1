#######################################################################
# 3-setup-db2.ps1 — DBA Team: DB2 on Kubernetes
#
# Deploys IBM DB2 to the databases namespace, waits for readiness,
# creates schema, and seeds data.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  DBA TEAM — DB2 Database Setup on K8s" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Deploy DB2 ---
Write-Host "[1/4] Deploying IBM DB2 (this takes 3-5 minutes)..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/databases/db2.yaml")

# --- Step 2: Wait for DB2 to be ready ---
Write-Host "[2/4] Waiting for DB2 to initialize..." -ForegroundColor Yellow
Write-Host "       (DB2 needs ~3 minutes for first-time setup)" -ForegroundColor Gray

$maxRetries = 20
$db2Ready = $false
for ($i = 1; $i -le $maxRetries; $i++) {
    Write-Host "       Attempt $i/$maxRetries..." -ForegroundColor Gray

    # First check if pod is running
    $podStatus = kubectl get pod -l app=db2 -n databases -o jsonpath="{.items[0].status.phase}" 2>$null
    if ($podStatus -ne "Running") {
        Write-Host "       Pod status: $podStatus — waiting..." -ForegroundColor Gray
        Start-Sleep -Seconds 30
        continue
    }

    # Then check if DB2 is ready
    $db2Pod = kubectl get pod -l app=db2 -n databases -o jsonpath="{.items[0].metadata.name}"
    $result = kubectl exec $db2Pod -n databases -- su - db2inst1 -c "db2 CONNECT TO empdb" 2>&1
    if ($result -match "Database Connection Information") {
        Write-Host "       DB2 is ready!" -ForegroundColor Green
        $db2Ready = $true
        break
    }
    Start-Sleep -Seconds 30
}

if (-not $db2Ready) {
    Write-Host "       ERROR: DB2 did not start in time!" -ForegroundColor Red
    exit 1
}

# --- Step 3: Create schema and seed data ---
Write-Host "[3/4] Creating employees table and seeding data..." -ForegroundColor Yellow
$db2Pod = kubectl get pod -l app=db2 -n databases -o jsonpath="{.items[0].metadata.name}"
$initSql = Join-Path $ProjectDir "employee-app/sql/init.sql"
kubectl cp $initSql "databases/${db2Pod}:/tmp/init.sql"

$sqlResult = kubectl exec $db2Pod -n databases -- su - db2inst1 -c "db2 CONNECT TO empdb && db2 -tvf /tmp/init.sql" 2>&1
Write-Host "       $sqlResult" -ForegroundColor Gray

# --- Step 4: Verify ---
Write-Host "[4/4] Verifying data..." -ForegroundColor Yellow
$countResult = kubectl exec $db2Pod -n databases -- su - db2inst1 -c "db2 CONNECT TO empdb && db2 'SELECT COUNT(*) AS TOTAL FROM employees'" 2>&1
Write-Host "       $countResult" -ForegroundColor Gray

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  DB2 setup COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Pod:       $db2Pod (databases namespace)"
Write-Host "  Database:  empdb"
Write-Host "  Port:      50000"
Write-Host "  User:      db2inst1"
Write-Host "  Service:   db2.databases.svc.cluster.local:50000"
Write-Host ""
Write-Host "  Next: Run .\scripts\4-setup-jenkins.ps1" -ForegroundColor Cyan
