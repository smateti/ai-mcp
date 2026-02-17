$Url = "http://localhost:9081/api/messages/count"

Write-Host "Polling queue depth (Ctrl+C to stop)..." -ForegroundColor Yellow
Write-Host ""

while ($true) {
    try {
        $result = Invoke-RestMethod -Uri $Url -Method Get -ErrorAction Stop
        $timestamp = Get-Date -Format "HH:mm:ss"
        Write-Host "[$timestamp] Queue depth: $($result.depth)" -ForegroundColor Cyan
    } catch {
        Write-Host "[$timestamp] Server not reachable" -ForegroundColor Red
    }
    Start-Sleep -Seconds 2
}
