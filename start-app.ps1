$Port = 9081
$ProjectDir = Join-Path $PSScriptRoot "jms-microprofile-app"

Write-Host "Checking if port $Port is in use..." -ForegroundColor Yellow

$conn = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1

if ($conn) {
    $procId = $conn.OwningProcess
    $procName = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
    Write-Host "Port $Port is in use by process '$procName' (PID: $procId). Killing it..." -ForegroundColor Red
    try {
        Stop-Process -Id $procId -Force -ErrorAction Stop
        Write-Host "Process killed." -ForegroundColor Green
    } catch {
        Write-Host "Stop-Process failed. Trying taskkill..." -ForegroundColor Yellow
        taskkill /PID $procId /F
    }
    Start-Sleep -Seconds 3
} else {
    Write-Host "Port $Port is free." -ForegroundColor Green
}

Write-Host "Building and starting Liberty server..." -ForegroundColor Yellow
Set-Location $ProjectDir
mvn clean package -DskipTests liberty:run
