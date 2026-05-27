# extract-all.ps1
# Calls the brd-agent REST endpoint to extract partial BRDs from ALL
# FoxPro source directories.
# Supports: .prg .scx .sct .sca .frx .frt .fra .mnx .mnt .mna .vcx .vct .vca .h
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File extract-all.ps1
#   powershell -ExecutionPolicy Bypass -File extract-all.ps1 -Clean

param(
    [switch]$Clean
)

$baseUrl   = "http://localhost:9082/extract"
$outputDir = "d:\apps\ws\ws32\brd-agent\brd\llama-parts"

# Every directory under FleetManagement that contains extractable files
$dirs = @(
    @{ Unit = "FleetManagement-programs";  Paths = @("programs") }
    @{ Unit = "FleetManagement-DVStuff";   Paths = @("DVStuff") }
    @{ Unit = "FleetManagement-Reports";   Paths = @("Reports") }
    @{ Unit = "FleetManagement-Forms";     Paths = @("Forms") }
    @{ Unit = "FleetManagement-menus";     Paths = @("menus") }
    @{ Unit = "FleetManagement-XLib";      Paths = @("XLib") }
    @{ Unit = "FleetManagement-WLib";      Paths = @("WLib") }
    @{ Unit = "FleetManagement-libs";      Paths = @("libs") }
)

# --- pre-flight ---
Write-Host ""
Write-Host "=== BRD Extraction - All Directories ===" -ForegroundColor Cyan
Write-Host "Endpoint  : $baseUrl"
Write-Host "Output    : $outputDir"
Write-Host "File types: .prg .scx .sct .sca .frx .frt .fra .mnx .mnt .mna .vcx .vct .vca .h"
Write-Host ""

# Health check
try {
    $health = Invoke-RestMethod -Uri "http://localhost:9082/health/ready" -TimeoutSec 5
    if ($health.status -ne "UP") { throw "not ready" }
    Write-Host "Server    : READY" -ForegroundColor Green
}
catch {
    Write-Host "Server    : NOT REACHABLE - start Liberty first!" -ForegroundColor Red
    Write-Host "  mvn -f d:\apps\ws\ws32\brd-agent\pom.xml liberty:start" -ForegroundColor Yellow
    exit 1
}

# Clean old output if requested
if ($Clean) {
    Write-Host ""
    Write-Host "Cleaning old output..." -ForegroundColor Yellow
    if (Test-Path $outputDir) {
        Remove-Item "$outputDir\*.json" -Force -ErrorAction SilentlyContinue
        Write-Host "  Removed all .json files from $outputDir"
    }
}

$startTs = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
Write-Host ""
Write-Host "Started   : $startTs"
Write-Host ""

$totalSucceeded = 0
$totalFailed    = 0
$totalProcessed = 0
$totalSkipped   = 0
$startTime      = Get-Date

foreach ($dir in $dirs) {
    $unitName    = $dir.Unit
    $sourcePaths = $dir.Paths

    $body = @{
        unitName    = $unitName
        sourcePaths = $sourcePaths
    } | ConvertTo-Json -Depth 3

    Write-Host "--- $unitName ---" -ForegroundColor Yellow
    $pathsList = $sourcePaths -join ", "
    Write-Host "  Scanning: $pathsList"

    $dirStart = Get-Date
    try {
        $response = Invoke-RestMethod -Uri $baseUrl `
            -Method Post `
            -ContentType "application/json" `
            -Body $body `
            -TimeoutSec 3600

        $succeeded = $response.files_succeeded
        $failed    = $response.files_failed
        $processed = $response.files_processed

        $totalSucceeded += $succeeded
        $totalFailed    += $failed
        $totalProcessed += $processed

        $elapsed = (Get-Date) - $dirStart
        $secs = [math]::Round($elapsed.TotalSeconds, 1)
        if ($elapsed.TotalSeconds -gt 0) {
            $rate = [math]::Round($processed / $elapsed.TotalSeconds, 1)
        } else {
            $rate = 0
        }

        Write-Host "  Result : $succeeded/$processed succeeded, $failed failed  (${secs}s, $rate files/s)" -ForegroundColor Green

        # List any failures
        if ($failed -gt 0) {
            $failures = $response.results | Where-Object { $_.success -eq $false }
            foreach ($f in $failures) {
                $fPath = $f.source_file
                $fErr  = $f.error_message
                Write-Host "    FAIL: $fPath - $fErr" -ForegroundColor Red
            }
        }
    }
    catch {
        $errMsg = $_.Exception.Message
        if ($errMsg -match "404" -or $errMsg -match "No FoxPro") {
            Write-Host "  Skipped: no supported files found" -ForegroundColor DarkGray
            $totalSkipped++
        } else {
            Write-Host "  ERROR: $errMsg" -ForegroundColor Red
        }
    }
    Write-Host ""
}

$totalElapsed = (Get-Date) - $startTime
$elapsedMin = [math]::Round($totalElapsed.TotalMinutes, 1)

if (Test-Path $outputDir) {
    $fileCount = (Get-ChildItem -Path $outputDir -Filter "*.json" | Measure-Object).Count
} else {
    $fileCount = 0
}

$dirCount = $dirs.Count

if ($totalFailed -gt 0) { $failColor = "Red" } else { $failColor = "Green" }

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "=== Summary ===" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Directories : $dirCount scanned ($totalSkipped empty)"
Write-Host "  Processed   : $totalProcessed files"
Write-Host "  Succeeded   : $totalSucceeded" -ForegroundColor Green
Write-Host "  Failed      : $totalFailed" -ForegroundColor $failColor
Write-Host "  JSON files  : $fileCount in $outputDir"
Write-Host "  Elapsed     : $elapsedMin minutes"
Write-Host ""
Write-Host "Done." -ForegroundColor Cyan
