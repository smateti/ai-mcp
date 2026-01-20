# Elasticsearch Stop Script (PowerShell)
# This script stops Elasticsearch Docker containers

param(
    [switch]$RemoveVolumes
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$composeFile = Join-Path $ScriptDir "elasticsearch-docker-compose.yml"

Write-Host "Stopping Elasticsearch..." -ForegroundColor Yellow

if (-not (Test-Path $composeFile)) {
    Write-Host "ERROR: Docker Compose file not found at: $composeFile" -ForegroundColor Red
    exit 1
}

try {
    if ($RemoveVolumes) {
        Write-Host "Stopping and removing volumes (data will be deleted)..." -ForegroundColor Red
        docker-compose -f $composeFile down -v
    } else {
        docker-compose -f $composeFile down
    }
    Write-Host "Elasticsearch stopped successfully" -ForegroundColor Green
}
catch {
    Write-Host "ERROR: Failed to stop Elasticsearch: $_" -ForegroundColor Red
    exit 1
}
