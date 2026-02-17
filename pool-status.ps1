param(
    [Parameter(Position=0)]
    [ValidateSet("status", "purge", "purge-immediate", "watch")]
    [string]$Action = "status"
)

$AdminUser = "admin"
$AdminPass = "admin123"
$BaseUrl = "https://localhost:9444/IBMJMXConnectorREST"
$MBeanUrl = "$BaseUrl/mbeans/WebSphere%3AjndiName%3Djms%2FSimpleConnectionFactory%2Cname%3Djms%2FSimpleConnectionFactory%2FconnectionManager%2Ctype%3Dcom.ibm.ws.jca.cm.mbean.ConnectionManagerMBean"

# Trust self-signed cert
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

function Get-PoolContents {
    $uri = "$MBeanUrl/operations/showPoolContents"
    $body = '{"params":[],"signature":[]}'
    $result = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $body
    return $result.value
}

switch ($Action) {
    "status" {
        Write-Host "Connection Pool Status (jms/SimpleConnectionFactory)" -ForegroundColor Yellow
        Write-Host "=====================================================" -ForegroundColor Yellow
        try {
            $pool = Get-PoolContents
            $pool -split '\\r\\n' | ForEach-Object {
                $line = $_.Trim()
                switch -Regex ($line) {
                    '^maxPoolSize='  { Write-Host "  Max Pool Size:   $($line -replace 'maxPoolSize=','')" -ForegroundColor Cyan }
                    '^size='         { Write-Host "  Current Size:    $($line -replace 'size=','')" -ForegroundColor Cyan }
                    '^waiting='      { Write-Host "  Waiting:         $($line -replace 'waiting=','')" -ForegroundColor Cyan }
                    '^unshared='     { Write-Host "  In Use (unsh):   $($line -replace 'unshared=','')" -ForegroundColor Cyan }
                    '^shared='       { Write-Host "  In Use (shared): $($line -replace 'shared=','')" -ForegroundColor Cyan }
                    '^available='    { Write-Host "  Available:       $($line -replace 'available=','')" -ForegroundColor Green }
                    'ManagedConnection' { Write-Host "  $line" -ForegroundColor Gray }
                }
            }
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "purge" {
        Write-Host "Purging connection pool..." -ForegroundColor Yellow
        try {
            $uri = "$MBeanUrl/operations/purgePoolContents"
            $body = '{"params":[{"value":"normal","type":"java.lang.String"}],"signature":["java.lang.String"]}'
            Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $body | Out-Null
            Write-Host "Connection pool purged." -ForegroundColor Green
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "purge-immediate" {
        Write-Host "Purging connection pool immediately..." -ForegroundColor Yellow
        try {
            $uri = "$MBeanUrl/operations/purgePoolContents"
            $body = '{"params":[{"value":"immediate","type":"java.lang.String"}],"signature":["java.lang.String"]}'
            Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $body | Out-Null
            Write-Host "Connection pool purged immediately." -ForegroundColor Green
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "watch" {
        Write-Host "Polling connection pool (Ctrl+C to stop)..." -ForegroundColor Yellow
        Write-Host ""
        while ($true) {
            try {
                $pool = Get-PoolContents
                $timestamp = Get-Date -Format "HH:mm:ss"
                $size = if ($pool -match 'size=(\d+)') { $matches[1] } else { "?" }
                $available = if ($pool -match 'available=(\d+)') { $matches[1] } else { "?" }
                $unshared = if ($pool -match 'unshared=(\d+)') { $matches[1] } else { "?" }
                $waiting = if ($pool -match 'waiting=(\d+)') { $matches[1] } else { "?" }
                $max = if ($pool -match 'maxPoolSize=(\d+)') { $matches[1] } else { "?" }
                Write-Host "[$timestamp] size=$size  available=$available  inUse=$unshared  waiting=$waiting  max=$max" -ForegroundColor Cyan
            } catch {
                Write-Host "[$timestamp] Server not reachable" -ForegroundColor Red
            }
            Start-Sleep -Seconds 2
        }
    }
}
