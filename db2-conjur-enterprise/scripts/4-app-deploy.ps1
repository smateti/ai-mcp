#######################################################################
# 4-app-deploy.ps1 — Operations / Deploy Team
#
# Fetches ALL secrets from Conjur using the app's host identity,
# then starts Liberty locally with injected environment variables.
# This mimics how K8s/OpenShift would inject secrets into a pod.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$KeysFile = Join-Path $ScriptDir ".conjur-keys"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  OPERATIONS — App Deployment" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Read credentials ---
Write-Host "[1/4] Reading deployment credentials..." -ForegroundColor Yellow
if (-not (Test-Path $KeysFile)) {
    Write-Host "       ERROR: $KeysFile not found. Run 1-conjur-setup.ps1 first!" -ForegroundColor Red
    exit 1
}
$keys = Get-Content $KeysFile | ConvertFrom-StringData
$hostApiKey = $keys.APP_HOST_API_KEY
$conjurUrl = $keys.CONJUR_URL
$conjurAccount = $keys.CONJUR_ACCOUNT

Write-Host "       Conjur URL: $conjurUrl" -ForegroundColor Gray
Write-Host "       Account:    $conjurAccount" -ForegroundColor Gray

# --- Step 2: Authenticate as app host ---
Write-Host "[2/4] Authenticating as host/myapp/liberty-app..." -ForegroundColor Yellow
$encodedLogin = [System.Uri]::EscapeDataString("host/myapp/liberty-app")
$tokenBytes = Invoke-RestMethod -Uri "$conjurUrl/authn/$conjurAccount/$encodedLogin/authenticate" `
    -Method Post -Body $hostApiKey -ContentType "text/plain"
$token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
$authHeader = @{ Authorization = "Token token=`"$token`"" }
Write-Host "       Authenticated successfully" -ForegroundColor Green

# --- Step 3: Fetch secrets from Conjur ---
Write-Host "[3/4] Fetching secrets from Conjur vault..." -ForegroundColor Yellow
$secretPaths = @("db%2Fusername", "db%2Fpassword", "db%2Furl", "myapp%2Fapi-key", "myapp%2Fapp-name")
$secrets = @{}

foreach ($path in $secretPaths) {
    $value = Invoke-RestMethod -Uri "$conjurUrl/secrets/$conjurAccount/variable/$path" `
        -Method Get -Headers $authHeader
    $key = [System.Uri]::UnescapeDataString($path)
    $secrets[$key] = $value
    $displayVal = if ($key -match "password") { "********" } else { $value }
    Write-Host "       $key = $displayVal" -ForegroundColor Gray
}

# --- Step 4: Set env vars and start Liberty ---
Write-Host "[4/4] Starting Open Liberty with Conjur secrets..." -ForegroundColor Yellow
Write-Host ""

# Parse DB URL to extract host/port/name (jdbc:db2://localhost:50000/empdb)
$dbUrl = $secrets["db/url"]
if ($dbUrl -match "jdbc:db2://([^:]+):(\d+)/(.+)") {
    $dbHost = $Matches[1]
    $dbPort = $Matches[2]
    $dbName = $Matches[3]
} else {
    $dbHost = "localhost"
    $dbPort = "50000"
    $dbName = "empdb"
}

# Set environment variables for Liberty
$env:CONJUR_APPLIANCE_URL = $conjurUrl
$env:CONJUR_ACCOUNT = $conjurAccount
$env:CONJUR_AUTHN_LOGIN = "host/myapp/liberty-app"
$env:CONJUR_AUTHN_API_KEY = $hostApiKey
$env:DB_HOST = $dbHost
$env:DB_PORT = $dbPort
$env:DB_NAME = $dbName
$env:DB_USERNAME = $secrets["db/username"]
$env:DB_PASSWORD = $secrets["db/password"]

Write-Host "============================================" -ForegroundColor Green
Write-Host "  Secrets injected. Starting Liberty..." -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  DB_HOST:     $dbHost"
Write-Host "  DB_PORT:     $dbPort"
Write-Host "  DB_NAME:     $dbName"
Write-Host "  DB_USERNAME: $($secrets['db/username'])"
Write-Host "  DB_PASSWORD: ********"
Write-Host ""
Write-Host "  App URL:     http://localhost:9080/api/employees" -ForegroundColor Cyan
Write-Host "  Health:      http://localhost:9080/health" -ForegroundColor Cyan
Write-Host "  OpenAPI:     http://localhost:9080/openapi/ui/" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Press Ctrl+C to stop Liberty." -ForegroundColor Gray
Write-Host ""

# Start Liberty in foreground
Push-Location $ProjectDir
try {
    mvn liberty:run
} finally {
    Pop-Location
}
