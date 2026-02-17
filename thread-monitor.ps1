param(
    [Parameter(Position=0)]
    [ValidateSet("status", "watch", "blocked", "deadlocks", "long-waiting", "dump", "reset-peak")]
    [string]$Action = "status"
)

$AdminUser = "admin"
$AdminPass = "admin123"
$BaseUrl = "https://localhost:9444/IBMJMXConnectorREST"
$ThreadingUrl = "$BaseUrl/mbeans/java.lang%3Atype%3DThreading"
$AppUrl = "http://localhost:9081/api/threads"

# Trust self-signed cert (for MBean fallback)
if (-not ([System.Management.Automation.PSTypeName]'TrustAll').Type) {
    Add-Type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAll : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int problem) { return true; }
}
"@
}
[System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAll
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$pair = "${AdminUser}:${AdminPass}"
$bytes = [System.Text.Encoding]::ASCII.GetBytes($pair)
$base64 = [System.Convert]::ToBase64String($bytes)
$headers = @{ Authorization = "Basic $base64" }

function Get-ThreadAttr($attr) {
    $result = Invoke-RestMethod -Uri "$ThreadingUrl/attributes/$attr" -Method Get -Headers $headers
    return $result.value
}

switch ($Action) {
    "status" {
        Write-Host "Thread Pool Status" -ForegroundColor Yellow
        Write-Host "==================" -ForegroundColor Yellow
        try {
            $result = Invoke-RestMethod -Uri $AppUrl -Method Get
            Write-Host "  Total threads:       $($result.totalThreads)" -ForegroundColor Cyan
            Write-Host "  Peak threads:        $($result.peakThreads)" -ForegroundColor $(if ($result.peakThreads -gt 50) {"Red"} else {"Cyan"})
            Write-Host "  Total started:       $($result.totalStarted)" -ForegroundColor Cyan
            Write-Host "  ----" -ForegroundColor Gray
            Write-Host "  Runnable:            $($result.runnable)" -ForegroundColor Green
            Write-Host "  Waiting:             $($result.waiting)" -ForegroundColor Cyan
            Write-Host "  Timed Waiting:       $($result.timedWaiting)" -ForegroundColor Cyan
            Write-Host "  Blocked:             $($result.blocked)" -ForegroundColor $(if ($result.blocked -gt 0) {"Red"} else {"Cyan"})
        } catch {
            Write-Host "  App endpoint not available, falling back to MBeans..." -ForegroundColor Gray
            try {
                $current = Get-ThreadAttr "ThreadCount"
                $peak    = Get-ThreadAttr "PeakThreadCount"
                $daemon  = Get-ThreadAttr "DaemonThreadCount"
                $started = Get-ThreadAttr "TotalStartedThreadCount"
                Write-Host "  Current threads:     $current" -ForegroundColor Cyan
                Write-Host "  Peak threads:        $peak" -ForegroundColor $(if ($peak -gt 50) {"Red"} else {"Cyan"})
                Write-Host "  Daemon threads:      $daemon" -ForegroundColor Cyan
                Write-Host "  Total ever started:  $started" -ForegroundColor Cyan
            } catch {
                Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
            }
        }
    }
    "watch" {
        Write-Host "Monitoring threads (Ctrl+C to stop)..." -ForegroundColor Yellow
        Write-Host ""
        "{0,-10}  {1,7}  {2,7}  {3,7}  {4,7}  {5,7}  {6,7}" -f "TIME", "TOTAL", "RUNNBL", "WAIT", "TIMED", "BLOCK", "PEAK" | Write-Host -ForegroundColor White
        "{0,-10}  {1,7}  {2,7}  {3,7}  {4,7}  {5,7}  {6,7}" -f "--------", "-----", "------", "----", "-----", "-----", "----" | Write-Host -ForegroundColor Gray
        while ($true) {
            $timestamp = Get-Date -Format "HH:mm:ss"
            try {
                $result = Invoke-RestMethod -Uri $AppUrl -Method Get
                $color = if ($result.blocked -gt 0) {"Red"} elseif ($result.totalThreads -gt 50) {"Yellow"} else {"Cyan"}
                "{0,-10}  {1,7}  {2,7}  {3,7}  {4,7}  {5,7}  {6,7}" -f $timestamp, $result.totalThreads, $result.runnable, $result.waiting, $result.timedWaiting, $result.blocked, $result.peakThreads | Write-Host -ForegroundColor $color
            } catch {
                Write-Host "[$timestamp] Server not reachable" -ForegroundColor Red
            }
            Start-Sleep -Seconds 2
        }
    }
    "blocked" {
        Write-Host "Blocked & Waiting Threads" -ForegroundColor Yellow
        Write-Host "=========================" -ForegroundColor Yellow
        try {
            $result = Invoke-RestMethod -Uri "$AppUrl/blocked" -Method Get
            Write-Host "  Count: $($result.count)" -ForegroundColor Cyan
            Write-Host ""
            foreach ($t in $result.blockedAndWaiting) {
                $stateColor = if ($t.state -eq "BLOCKED") {"Red"} else {"Yellow"}
                Write-Host "  [$($t.state)]" -ForegroundColor $stateColor -NoNewline
                Write-Host " $($t.name) (id=$($t.id))" -ForegroundColor White
                if ($t.lockName) {
                    Write-Host "    Lock: $($t.lockName)" -ForegroundColor Gray
                }
                if ($t.lockOwner) {
                    Write-Host "    Lock Owner: $($t.lockOwner) (id=$($t.lockOwnerId))" -ForegroundColor Gray
                }
                Write-Host "    Blocked: $($t.blockedTimeMs)ms ($($t.blockedCount)x)  Waited: $($t.waitedTimeMs)ms ($($t.waitedCount)x)" -ForegroundColor Gray
                foreach ($frame in $t.stackTrace) {
                    Write-Host "      at $frame" -ForegroundColor DarkGray
                }
                Write-Host ""
            }
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "deadlocks" {
        Write-Host "Deadlock Detection" -ForegroundColor Yellow
        Write-Host "==================" -ForegroundColor Yellow
        try {
            $result = Invoke-RestMethod -Uri "$AppUrl/deadlocks" -Method Get
            if ($result.deadlocks -eq $false) {
                Write-Host "  No deadlocks detected" -ForegroundColor Green
            } else {
                Write-Host "  DEADLOCKS FOUND: $($result.count) threads" -ForegroundColor Red
                Write-Host ""
                foreach ($t in $result.threads) {
                    Write-Host "  Thread: $($t.name) (id=$($t.id)) [$($t.state)]" -ForegroundColor Red
                    if ($t.lockName) { Write-Host "    Waiting on: $($t.lockName)" -ForegroundColor Yellow }
                    if ($t.lockOwner) { Write-Host "    Held by: $($t.lockOwner)" -ForegroundColor Yellow }
                    foreach ($frame in $t.stackTrace) {
                        Write-Host "      at $frame" -ForegroundColor DarkGray
                    }
                    Write-Host ""
                }
            }
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "long-waiting" {
        Write-Host "Long Waiting Threads (stuck / I/O bound)" -ForegroundColor Yellow
        Write-Host "=========================================" -ForegroundColor Yellow
        try {
            $result = Invoke-RestMethod -Uri "$AppUrl/long-waiting" -Method Get
            $threads = $result.longWaitingThreads
            if ($threads.Count -eq 0) {
                Write-Host "  No long-waiting threads detected" -ForegroundColor Green
            } else {
                Write-Host "  Found $($threads.Count) thread(s) with excessive wait time" -ForegroundColor Red
                Write-Host ""
                foreach ($t in $threads) {
                    Write-Host "  $($t.name) (id=$($t.id)) [$($t.state)]" -ForegroundColor Yellow
                    Write-Host "    CPU: $($t.cpuTimeMs)ms  Blocked: $($t.blockedTimeMs)ms  Waited: $($t.waitedTimeMs)ms  Total Wait: $($t.totalWaitMs)ms" -ForegroundColor Cyan
                    $ratio = $t.waitToCpuRatio
                    if ($ratio -eq -1) { $ratioStr = "Inf (0 CPU)" } else { $ratioStr = "${ratio}x" }
                    Write-Host "    Wait-to-CPU ratio: $ratioStr" -ForegroundColor $(if ($ratio -gt 100 -or $ratio -eq -1) {"Red"} else {"Yellow"})
                    foreach ($frame in $t.stackTrace) {
                        Write-Host "      at $frame" -ForegroundColor DarkGray
                    }
                    Write-Host ""
                }
            }
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "dump" {
        Write-Host "Triggering thread dump..." -ForegroundColor Yellow
        try {
            $uri = "$BaseUrl/mbeans/WebSphere%3Atype%3DJavaDiagnostics/operations/dumpJava"
            $body = '{"params":[{"value":"thread","type":"java.lang.String"}],"signature":["java.lang.String"]}'
            $result = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $body
            Write-Host "Thread dump generated: $($result.value)" -ForegroundColor Green
            Write-Host "Check server logs directory for the dump file." -ForegroundColor Cyan
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "reset-peak" {
        Write-Host "Resetting peak thread count..." -ForegroundColor Yellow
        try {
            $uri = "$ThreadingUrl/operations/resetPeakThreadCount"
            Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body '{"params":[],"signature":[]}' | Out-Null
            Write-Host "Peak thread count reset." -ForegroundColor Green
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}
