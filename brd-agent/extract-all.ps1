# extract-all.ps1
# Calls the brd-agent REST endpoint to extract partial BRDs from all
# FoxPro .prg directories. Sends one request per directory so progress
# is visible and a single large directory doesn't block the others.

$baseUrl = "http://localhost:9082/extract"
$outputDir = "d:\apps\ws\ws32\brd-agent\brd\llama-parts"

# All directories containing .prg files
$dirs = @(
    @{ Unit = "FleetManagement-programs";  Paths = @("programs") }
    @{ Unit = "FleetManagement-DVStuff";   Paths = @("DVStuff") }
    @{ Unit = "FleetManagement-Reports";   Paths = @("Reports") }
    @{ Unit = "FleetManagement-XLib";      Paths = @("XLib") }
)

$totalSucceeded = 0
$totalFailed    = 0
$totalProcessed = 0
$startTime      = Get-Date

Write-Host "=== BRD Extraction - All Directories ===" -ForegroundColor Cyan
Write-Host "Endpoint : $baseUrl"
Write-Host "Output   : $outputDir"
Write-Host "Started  : $startTime"
Write-Host ""

foreach ($dir in $dirs) {
    $unitName   = $dir.Unit
    $sourcePaths = $dir.Paths

    $body = @{
        unitName    = $unitName
        sourcePaths = $sourcePaths
    } | ConvertTo-Json -Depth 3

    Write-Host "--- $unitName ---" -ForegroundColor Yellow
    Write-Host "  Sending: sourcePaths = [$($sourcePaths -join ', ')]"

    $dirStart = Get-Date
    try {
        $response = Invoke-RestMethod -Uri $baseUrl `
            -Method Post `
            -ContentType "application/json" `
            -Body $body `
            -TimeoutSec 1800

        $succeeded = $response.files_succeeded
        $failed    = $response.files_failed
        $processed = $response.files_processed

        $totalSucceeded += $succeeded
        $totalFailed    += $failed
        $totalProcessed += $processed

        $elapsed = (Get-Date) - $dirStart
        Write-Host "  Result : $succeeded/$processed succeeded, $failed failed  ($([math]::Round($elapsed.TotalSeconds,1))s)" -ForegroundColor Green

        # List any failures
        if ($failed -gt 0) {
            $failures = $response.results | Where-Object { $_.success -eq $false }
            foreach ($f in $failures) {
                Write-Host "    FAIL: $($f.source_file) - $($f.error_message)" -ForegroundColor Red
            }
        }
    }
    catch {
        Write-Host "  ERROR: $_" -ForegroundColor Red
    }
    Write-Host ""
}

$totalElapsed = (Get-Date) - $startTime
$fileCount    = (Get-ChildItem -Path $outputDir -Filter "*.json" | Measure-Object).Count

Write-Host "=== Summary ===" -ForegroundColor Cyan
Write-Host "  Processed : $totalProcessed"
Write-Host "  Succeeded : $totalSucceeded" -ForegroundColor Green
Write-Host "  Failed    : $totalFailed" -ForegroundColor $(if ($totalFailed -gt 0) { "Red" } else { "Green" })
Write-Host "  JSON files: $fileCount in $outputDir"
Write-Host "  Elapsed   : $([math]::Round($totalElapsed.TotalMinutes,1)) minutes"
Write-Host ""
Write-Host "Done." -ForegroundColor Cyan
