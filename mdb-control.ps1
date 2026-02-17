param(
    [Parameter(Position=0)]
    [ValidateSet("pause", "resume", "status", "list")]
    [string]$Action = "status"
)

$AdminUser = "admin"
$AdminPass = "admin123"
$BaseUrl = "https://localhost:9444/IBMJMXConnectorREST"
$MBeanUrl = "$BaseUrl/mbeans/WebSphere%3Afeature%3Dkernel%2Cname%3DServerEndpointControl"
$Target = "jms-microprofile-app#jms-microprofile-app.war#JmsConsumerMDB"
$TargetParam = '{"params":[{"value":"' + $Target + '","type":"java.lang.String"}],"signature":["java.lang.String"]}'

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

switch ($Action) {
    "list" {
        Write-Host "Listing all pausable endpoints..." -ForegroundColor Yellow
        try {
            $uri = "$MBeanUrl/operations/listEndpoints"
            $body = '{"params":[],"signature":[]}'
            $result = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $body
            $result.value | ForEach-Object { Write-Host "  $_" -ForegroundColor Cyan }
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "pause" {
        Write-Host "Pausing activation spec via MBean..." -ForegroundColor Yellow
        try {
            $uri = "$MBeanUrl/operations/pause"
            Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $TargetParam | Out-Null
            Write-Host "Activation spec PAUSED - messages will queue up" -ForegroundColor Red
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "resume" {
        Write-Host "Resuming activation spec via MBean..." -ForegroundColor Yellow
        try {
            $uri = "$MBeanUrl/operations/resume"
            Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $TargetParam | Out-Null
            Write-Host "Activation spec RESUMED - consuming messages" -ForegroundColor Green
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    "status" {
        Write-Host "Checking activation spec status..." -ForegroundColor Yellow
        try {
            $uri = "$MBeanUrl/operations/isPaused"
            $result = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -ContentType "application/json" -Body $TargetParam
            if ($result.value -eq "true") {
                Write-Host "Activation spec status: PAUSED" -ForegroundColor Red
            } else {
                Write-Host "Activation spec status: RUNNING" -ForegroundColor Green
            }
        } catch {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}
