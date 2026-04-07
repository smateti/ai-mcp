#######################################################################
# 6-cleanup.ps1 — Teardown
#
# Stops and removes all containers and networks created by the scripts.
#######################################################################

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$KeysFile = Join-Path $ScriptDir ".conjur-keys"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  CLEANUP — Teardown All Resources" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Stop and remove containers
$containers = @("conjur-server", "conjur-db", "app-db2")
foreach ($container in $containers) {
    Write-Host "Stopping $container..." -ForegroundColor Yellow
    docker stop $container 2>$null
    docker rm $container 2>$null
}

# Remove network
Write-Host "Removing Docker network 'conjur-net'..." -ForegroundColor Yellow
docker network rm conjur-net 2>$null

# Remove keys file
if (Test-Path $KeysFile) {
    Remove-Item $KeysFile
    Write-Host "Removed .conjur-keys" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Cleanup COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  All containers stopped and removed."
Write-Host "  All credentials cleared."
