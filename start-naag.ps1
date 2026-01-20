# NAAG Platform Startup Script
# Nimbus AI Agent - Start all services in dependency order

param(
    [switch]$StopAll,
    [switch]$Status,
    [string]$Service
)

$services = @(
    @{ Name = "naag-rag-service";      Port = 8080; Dir = "naag-rag-service" },
    @{ Name = "naag-tool-registry";    Port = 8081; Dir = "naag-tool-registry" },
    @{ Name = "naag-mcp-gateway";      Port = 8082; Dir = "naag-mcp-gateway" },
    @{ Name = "naag-utility-tools";    Port = 8083; Dir = "naag-utility-tools" },
    @{ Name = "naag-category-admin";   Port = 8085; Dir = "naag-category-admin" },
    @{ Name = "naag-ai-orchestrator";  Port = 8086; Dir = "naag-ai-orchestrator" },
    @{ Name = "naag-chat-app";         Port = 8087; Dir = "naag-chat-app" }
)

$basePath = "d:\apps\ws\ws8"
$logDir = "$basePath\logs"
$pidFile = "$basePath\.naag-pids.json"

# Create logs directory if not exists
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

# Load PIDs from file
function Load-Pids {
    if (Test-Path $pidFile) {
        try {
            $content = Get-Content $pidFile -Raw
            return $content | ConvertFrom-Json -AsHashtable
        } catch {
            return @{}
        }
    }
    return @{}
}

# Save PIDs to file
function Save-Pids {
    param($pids)
    $pids | ConvertTo-Json | Set-Content $pidFile
}

# Check if process is running by PID
function Is-ProcessRunning {
    param([int]$ProcessId)
    if ($ProcessId -eq 0) { return $false }
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    return $null -ne $process
}

# Kill process by PID
function Kill-Process {
    param([int]$ProcessId, [string]$ServiceName)

    if ($ProcessId -eq 0) { return }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($process) {
        Write-Host "    Killing $ServiceName (PID: $ProcessId)..." -ForegroundColor Yellow
        Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
}

# Check port as fallback
function Check-Port {
    param([int]$Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    return $null -ne $connection
}

# Kill process on port (fallback)
function Kill-ProcessOnPort {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if ($connection) {
        $processId = $connection.OwningProcess | Select-Object -First 1
        if ($processId) {
            $processName = (Get-Process -Id $processId -ErrorAction SilentlyContinue).ProcessName
            Write-Host "    Killing process $processName (PID: $processId) on port $Port..." -ForegroundColor Yellow
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
        }
    }
}

function Start-Service {
    param($svc)

    $servicePath = "$basePath\$($svc.Dir)"
    $logFile = "$logDir\$($svc.Name).log"
    $pids = Load-Pids

    if (-not (Test-Path $servicePath)) {
        Write-Host "  [SKIP] $($svc.Name) - Directory not found" -ForegroundColor Yellow
        return
    }

    # Check if we have a stored PID and kill it
    $storedPid = $pids[$svc.Name]
    if ($storedPid -and (Is-ProcessRunning $storedPid)) {
        Write-Host "  [RESTART] $($svc.Name) - Killing existing process (PID: $storedPid)..." -ForegroundColor Yellow
        Kill-Process $storedPid $svc.Name
    }
    # Fallback: check port
    elseif (Check-Port $svc.Port) {
        Write-Host "  [RESTART] $($svc.Name) - Port $($svc.Port) in use, killing process..." -ForegroundColor Yellow
        Kill-ProcessOnPort $svc.Port
        Start-Sleep -Seconds 2
    }

    Write-Host "  [STARTING] $($svc.Name) on port $($svc.Port)..." -ForegroundColor Cyan

    $process = Start-Process -FilePath "mvn" `
        -ArgumentList "clean spring-boot:run" `
        -WorkingDirectory $servicePath `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError "$logDir\$($svc.Name)-error.log" `
        -PassThru `
        -WindowStyle Hidden

    # Store the PID
    $pids[$svc.Name] = $process.Id
    Save-Pids $pids

    # Wait for service to start (max 60 seconds)
    $timeout = 60
    $elapsed = 0
    while (-not (Check-Port $svc.Port) -and $elapsed -lt $timeout) {
        Start-Sleep -Seconds 2
        $elapsed += 2
        Write-Host "    Waiting... ($elapsed s)" -ForegroundColor Gray
    }

    if (Check-Port $svc.Port) {
        Write-Host "  [STARTED] $($svc.Name) on port $($svc.Port) (PID: $($process.Id))" -ForegroundColor Green
    } else {
        Write-Host "  [FAILED] $($svc.Name) - Check $logFile" -ForegroundColor Red
    }
}

function Stop-Service {
    param($svc)

    $pids = Load-Pids
    $storedPid = $pids[$svc.Name]

    # Try stored PID first
    if ($storedPid -and (Is-ProcessRunning $storedPid)) {
        Write-Host "  [STOPPING] $($svc.Name) (PID: $storedPid)..." -ForegroundColor Yellow
        Kill-Process $storedPid $svc.Name
        $pids[$svc.Name] = 0
        Save-Pids $pids
        Write-Host "  [STOPPED] $($svc.Name)" -ForegroundColor Green
        return
    }

    # Fallback: check port
    if (Check-Port $svc.Port) {
        Write-Host "  [STOPPING] $($svc.Name) on port $($svc.Port)..." -ForegroundColor Yellow
        Kill-ProcessOnPort $svc.Port
        $pids[$svc.Name] = 0
        Save-Pids $pids
        Write-Host "  [STOPPED] $($svc.Name)" -ForegroundColor Green
        return
    }

    Write-Host "  [STOPPED] $($svc.Name)" -ForegroundColor Gray
}

function Show-Status {
    Write-Host "`nNAAG Platform Status:" -ForegroundColor Cyan
    Write-Host "=====================" -ForegroundColor Cyan

    $pids = Load-Pids

    foreach ($svc in $services) {
        $storedPid = $pids[$svc.Name]
        $isRunning = $false
        $displayPid = 0

        # Check stored PID first
        if ($storedPid -and (Is-ProcessRunning $storedPid)) {
            $isRunning = $true
            $displayPid = $storedPid
        }
        # Fallback: check port
        elseif (Check-Port $svc.Port) {
            $isRunning = $true
            $connection = Get-NetTCPConnection -LocalPort $svc.Port -ErrorAction SilentlyContinue
            $displayPid = $connection.OwningProcess | Select-Object -First 1
        }

        if ($isRunning) {
            Write-Host "  [RUNNING] $($svc.Name) - http://localhost:$($svc.Port) (PID: $displayPid)" -ForegroundColor Green
        } else {
            Write-Host "  [STOPPED] $($svc.Name) - port $($svc.Port)" -ForegroundColor Red
        }
    }
    Write-Host ""
}

# Main execution
Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "  NAAG - Nimbus AI Agent Platform" -ForegroundColor Magenta
Write-Host "========================================`n" -ForegroundColor Magenta

if ($Status) {
    Show-Status
    exit 0
}

if ($StopAll) {
    Write-Host "Stopping all services...`n" -ForegroundColor Yellow
    foreach ($svc in $services | Sort-Object { $_.Port } -Descending) {
        Stop-Service $svc
    }
    Write-Host "`nAll services stopped.`n" -ForegroundColor Green
    exit 0
}

if ($Service) {
    $svc = $services | Where-Object { $_.Name -eq $Service }
    if ($svc) {
        Write-Host "Starting $Service...`n" -ForegroundColor Cyan
        Start-Service $svc
    } else {
        Write-Host "Service not found: $Service" -ForegroundColor Red
        Write-Host "Available services:" -ForegroundColor Yellow
        $services | ForEach-Object { Write-Host "  - $($_.Name)" }
    }
    exit 0
}

# Start all services in dependency order
Write-Host "Starting all services in dependency order...`n" -ForegroundColor Cyan

foreach ($svc in $services) {
    Start-Service $svc
}

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "  Startup Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Magenta

Show-Status

Write-Host "Useful commands:" -ForegroundColor Yellow
Write-Host "  .\start-naag.ps1 -Status      # Check service status"
Write-Host "  .\start-naag.ps1 -StopAll     # Stop all services"
Write-Host "  .\start-naag.ps1 -Service naag-chat-app  # Start single service"
Write-Host ""
