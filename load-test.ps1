$BaseUrl = "http://localhost:9081/api/messages"
$TotalMessages = 1000
$SuccessCount = 0
$FailCount = 0

Write-Host "Sending $TotalMessages messages to $BaseUrl ..." -ForegroundColor Yellow
Write-Host ""

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

for ($i = 1; $i -le $TotalMessages; $i++) {
    $body = "Message #$i - Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff')"
    try {
        $response = Invoke-RestMethod -Uri $BaseUrl -Method Post -Body $body -ContentType "text/plain" -ErrorAction Stop
        $SuccessCount++
    } catch {
        $FailCount++
    }

    if ($i % 100 -eq 0) {
        Write-Host "  Sent $i / $TotalMessages messages..." -ForegroundColor Cyan
    }
}

$stopwatch.Stop()
$elapsed = $stopwatch.Elapsed

Write-Host ""
Write-Host "===== Load Test Complete =====" -ForegroundColor Green
Write-Host "  Total:     $TotalMessages"
Write-Host "  Success:   $SuccessCount" -ForegroundColor Green
Write-Host "  Failed:    $FailCount" -ForegroundColor Red
Write-Host "  Duration:  $($elapsed.ToString('mm\:ss\.fff'))"
Write-Host "  Rate:      $([math]::Round($TotalMessages / $elapsed.TotalSeconds, 1)) msg/sec"
Write-Host ""

# Check queue depth after sending
Write-Host "Checking queue depth..." -ForegroundColor Yellow
try {
    $depth = Invoke-RestMethod -Uri "$BaseUrl/count" -Method Get -ErrorAction Stop
    Write-Host "  Queue depth: $($depth.depth)" -ForegroundColor Cyan
} catch {
    Write-Host "  Could not check queue depth: $_" -ForegroundColor Red
}
