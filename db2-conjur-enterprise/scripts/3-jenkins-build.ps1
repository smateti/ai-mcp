#######################################################################
# 3-jenkins-build.ps1 — Jenkins CI Pipeline
#
# Simulates a Jenkins pipeline: build, test, report.
# MicroShed tests are self-contained (Testcontainers spin up their
# own DB2 instance) — no dependency on standalone containers.
#######################################################################

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  JENKINS CI — Build Pipeline" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Stage 1: Build ---
Write-Host "========== STAGE: Build ==========" -ForegroundColor Yellow
Write-Host "Building application with Maven..." -ForegroundColor Gray
Push-Location $ProjectDir
try {
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "BUILD FAILED!" -ForegroundColor Red
        exit 1
    }
    Write-Host "Build SUCCESS" -ForegroundColor Green
    Write-Host ""

    # --- Stage 2: Integration Test ---
    Write-Host "========== STAGE: Integration Test ==========" -ForegroundColor Yellow
    Write-Host "Running MicroShed integration tests..." -ForegroundColor Gray
    Write-Host "(Testcontainers will start DB2 + Liberty automatically)" -ForegroundColor Gray
    Write-Host ""
    mvn failsafe:integration-test failsafe:verify
    $testExitCode = $LASTEXITCODE

    # --- Stage 3: Report ---
    Write-Host ""
    Write-Host "========== STAGE: Report ==========" -ForegroundColor Yellow
    if ($testExitCode -eq 0) {
        Write-Host "All integration tests PASSED" -ForegroundColor Green
    } else {
        Write-Host "Some tests FAILED — check target/failsafe-reports/" -ForegroundColor Red
    }

    # Show artifact info
    $warFile = Join-Path $ProjectDir "target/db2-conjur-enterprise.war"
    if (Test-Path $warFile) {
        $size = [math]::Round((Get-Item $warFile).Length / 1MB, 2)
        Write-Host ""
        Write-Host "  Artifact: db2-conjur-enterprise.war ($size MB)" -ForegroundColor Gray
        Write-Host "  Location: target/db2-conjur-enterprise.war" -ForegroundColor Gray
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Jenkins pipeline COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Next: Run .\scripts\4-app-deploy.ps1" -ForegroundColor Cyan
