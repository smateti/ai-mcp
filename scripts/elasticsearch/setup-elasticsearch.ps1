# Elasticsearch Setup Script (PowerShell)
# This script starts Elasticsearch via Docker Compose and creates the audit log index

param(
    [string]$EsHost = "localhost",
    [string]$EsPort = "9200",
    [switch]$SkipDocker,
    [switch]$SkipIndex
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  NAAG Elasticsearch Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Start Docker Compose
if (-not $SkipDocker) {
    Write-Host "[1/3] Starting Elasticsearch via Docker Compose..." -ForegroundColor Yellow

    $composeFile = Join-Path $ScriptDir "elasticsearch-docker-compose.yml"

    if (-not (Test-Path $composeFile)) {
        Write-Host "ERROR: Docker Compose file not found at: $composeFile" -ForegroundColor Red
        exit 1
    }

    try {
        docker-compose -f $composeFile up -d
        Write-Host "Docker Compose started successfully" -ForegroundColor Green
    }
    catch {
        Write-Host "ERROR: Failed to start Docker Compose: $_" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "[1/3] Skipping Docker Compose (--SkipDocker flag set)" -ForegroundColor Gray
}

# Step 2: Wait for Elasticsearch to be ready
Write-Host ""
Write-Host "[2/3] Waiting for Elasticsearch to be ready..." -ForegroundColor Yellow

$EsUrl = "http://${EsHost}:${EsPort}"
$maxAttempts = 30
$attempt = 0
$ready = $false

while ($attempt -lt $maxAttempts -and -not $ready) {
    $attempt++
    try {
        $response = Invoke-RestMethod -Uri "${EsUrl}/_cluster/health" -Method GET -TimeoutSec 5 -ErrorAction Stop
        if ($response.status -eq "green" -or $response.status -eq "yellow") {
            $ready = $true
            Write-Host "Elasticsearch is ready (status: $($response.status))" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "  Attempt $attempt/$maxAttempts - Elasticsearch not ready yet..." -ForegroundColor Gray
        Start-Sleep -Seconds 5
    }
}

if (-not $ready) {
    Write-Host "ERROR: Elasticsearch did not become ready in time" -ForegroundColor Red
    Write-Host "Check if Docker containers are running: docker ps" -ForegroundColor Yellow
    exit 1
}

# Step 3: Create the audit log index
if (-not $SkipIndex) {
    Write-Host ""
    Write-Host "[3/3] Creating audit log index..." -ForegroundColor Yellow

    $indexScript = Join-Path $ScriptDir "create-audit-index.ps1"

    if (Test-Path $indexScript) {
        & $indexScript -EsHost $EsHost -EsPort $EsPort
    } else {
        Write-Host "WARNING: Index creation script not found at: $indexScript" -ForegroundColor Yellow
        Write-Host "You may need to create the index manually" -ForegroundColor Yellow
    }
} else {
    Write-Host "[3/3] Skipping index creation (--SkipIndex flag set)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Setup Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Elasticsearch: ${EsUrl}" -ForegroundColor White
Write-Host "Kibana:        http://${EsHost}:5601" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Enable Elasticsearch in application.yml:" -ForegroundColor White
Write-Host "   naag.elasticsearch.enabled: true" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Restart naagi-category-admin" -ForegroundColor White
Write-Host ""
Write-Host "3. Trigger initial sync:" -ForegroundColor White
Write-Host "   curl -X POST http://localhost:8085/api/elasticsearch/sync/full" -ForegroundColor Gray
Write-Host ""
