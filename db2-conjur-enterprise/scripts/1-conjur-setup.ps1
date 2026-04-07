#######################################################################
# 1-conjur-setup.ps1 — Security / Platform Team
#
# Sets up CyberArk Conjur OSS + its PostgreSQL backend as standalone
# Docker containers. Loads RBAC policies and creates app host identity.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$KeysFile = Join-Path $ScriptDir ".conjur-keys"
$CONJUR_DATA_KEY = "6JXGcCVLYF8EjGvearzb3oabeYB2InGXIkPm5SF25ts="
$CONJUR_ACCOUNT = "myConjurAccount"
$CONJUR_URL = "http://localhost:8080"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  SECURITY TEAM — Conjur Vault Setup" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Create Docker network ---
Write-Host "[1/8] Creating Docker network 'conjur-net'..." -ForegroundColor Yellow
docker network create conjur-net 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "       Network 'conjur-net' may already exist, continuing..." -ForegroundColor Gray
}

# --- Step 2: Start PostgreSQL for Conjur ---
Write-Host "[2/8] Starting PostgreSQL (Conjur backend)..." -ForegroundColor Yellow
docker run -d `
    --name conjur-db `
    --network conjur-net `
    -e POSTGRES_HOST_AUTH_METHOD=password `
    -e POSTGRES_PASSWORD=SuperSecretPg `
    --restart unless-stopped `
    postgres:15

# Wait for Postgres
Write-Host "       Waiting for PostgreSQL to be ready..." -ForegroundColor Gray
for ($i = 1; $i -le 30; $i++) {
    $result = docker exec conjur-db pg_isready -U postgres 2>&1
    if ($result -match "accepting connections") { break }
    Start-Sleep -Seconds 2
}

# --- Step 3: Start Conjur server ---
Write-Host "[3/8] Starting CyberArk Conjur OSS server..." -ForegroundColor Yellow
docker run -d `
    --name conjur-server `
    --network conjur-net `
    -p 8080:80 `
    -e DATABASE_URL="postgres://postgres:SuperSecretPg@conjur-db/postgres" `
    -e CONJUR_DATA_KEY=$CONJUR_DATA_KEY `
    -e CONJUR_AUTHENTICATORS=authn `
    -e CONJUR_TELEMETRY_ENABLED=false `
    --restart unless-stopped `
    cyberark/conjur:latest server

# Wait for Conjur healthy
Write-Host "       Waiting for Conjur to be healthy..." -ForegroundColor Gray
for ($i = 1; $i -le 60; $i++) {
    try {
        $response = Invoke-WebRequest -Uri $CONJUR_URL -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) { break }
    } catch { }
    Start-Sleep -Seconds 3
}

# --- Step 4: Create Conjur account ---
Write-Host "[4/8] Creating Conjur account '$CONJUR_ACCOUNT'..." -ForegroundColor Yellow
$accountResult = docker exec conjur-server conjurctl account create $CONJUR_ACCOUNT 2>&1
$adminApiKey = ($accountResult | Select-String "API key for admin:").ToString().Split(":")[-1].Trim()

if ([string]::IsNullOrEmpty($adminApiKey)) {
    # Account may already exist — try to get the key from existing setup
    Write-Host "       Account may already exist. Trying to retrieve admin key..." -ForegroundColor Gray
    $adminApiKey = ($accountResult | Select-String "api_key").ToString() -replace '.*"api_key":"([^"]+)".*','$1'
}
Write-Host "       Admin API Key: $($adminApiKey.Substring(0,8))..." -ForegroundColor Green

# --- Step 5: Authenticate as admin ---
Write-Host "[5/8] Authenticating as admin..." -ForegroundColor Yellow
$tokenBytes = Invoke-RestMethod -Uri "$CONJUR_URL/authn/$CONJUR_ACCOUNT/admin/authenticate" `
    -Method Post -Body $adminApiKey -ContentType "text/plain"
$token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
$authHeader = @{ Authorization = "Token token=`"$token`"" }

# --- Step 6: Load root policy ---
Write-Host "[6/8] Loading root policy..." -ForegroundColor Yellow
$rootPolicy = Get-Content (Join-Path $ProjectDir "conjur/policy/root.yml") -Raw
Invoke-RestMethod -Uri "$CONJUR_URL/policies/$CONJUR_ACCOUNT/policy/root" `
    -Method Put -Headers $authHeader -Body $rootPolicy -ContentType "application/x-yaml"

# Re-authenticate (tokens are short-lived)
$tokenBytes = Invoke-RestMethod -Uri "$CONJUR_URL/authn/$CONJUR_ACCOUNT/admin/authenticate" `
    -Method Post -Body $adminApiKey -ContentType "text/plain"
$token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
$authHeader = @{ Authorization = "Token token=`"$token`"" }

# --- Step 7: Load myapp policy (creates host identity) ---
Write-Host "[7/8] Loading myapp policy (creates host identity)..." -ForegroundColor Yellow
$myappPolicy = Get-Content (Join-Path $ProjectDir "conjur/policy/myapp.yml") -Raw
$myappResult = Invoke-RestMethod -Uri "$CONJUR_URL/policies/$CONJUR_ACCOUNT/policy/myapp" `
    -Method Post -Headers $authHeader -Body $myappPolicy -ContentType "application/x-yaml"

# Extract host API key from response
$hostApiKey = ""
if ($myappResult.created_roles) {
    $hostRole = $myappResult.created_roles.PSObject.Properties | Where-Object { $_.Name -match "liberty-app" }
    if ($hostRole) {
        $hostApiKey = $hostRole.Value.api_key
    }
}
if ([string]::IsNullOrEmpty($hostApiKey)) {
    Write-Host "       WARNING: Could not extract host API key. Host may already exist." -ForegroundColor DarkYellow
    $hostApiKey = "MANUALLY_SET_THIS"
}

# Load db policy
Write-Host "[8/8] Loading db policy..." -ForegroundColor Yellow
# Re-authenticate
$tokenBytes = Invoke-RestMethod -Uri "$CONJUR_URL/authn/$CONJUR_ACCOUNT/admin/authenticate" `
    -Method Post -Body $adminApiKey -ContentType "text/plain"
$token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
$authHeader = @{ Authorization = "Token token=`"$token`"" }

$dbPolicy = Get-Content (Join-Path $ProjectDir "conjur/policy/db.yml") -Raw
Invoke-RestMethod -Uri "$CONJUR_URL/policies/$CONJUR_ACCOUNT/policy/db" `
    -Method Post -Headers $authHeader -Body $dbPolicy -ContentType "application/x-yaml"

# --- Save keys to file (simulates Jenkins Credential Store) ---
@"
CONJUR_ADMIN_API_KEY=$adminApiKey
APP_HOST_API_KEY=$hostApiKey
CONJUR_URL=$CONJUR_URL
CONJUR_ACCOUNT=$CONJUR_ACCOUNT
"@ | Set-Content $KeysFile

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Conjur setup COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Conjur URL:      $CONJUR_URL"
Write-Host "  Account:         $CONJUR_ACCOUNT"
Write-Host "  Admin API Key:   $($adminApiKey.Substring(0,8))..."
Write-Host "  Host API Key:    $($hostApiKey.Substring(0,8))..."
Write-Host "  Keys saved to:   $KeysFile"
Write-Host ""
Write-Host "  Next: Run .\scripts\2-dba-setup.ps1" -ForegroundColor Cyan
