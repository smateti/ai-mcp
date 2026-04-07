#######################################################################
# 2-dba-setup.ps1 — DBA Team
#
# Starts a standalone DB2 container, creates the schema, seeds data,
# and stores DB credentials in Conjur vault.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$KeysFile = Join-Path $ScriptDir ".conjur-keys"

# DB2 configuration
$DB_CONTAINER = "app-db2"
$DB_PASSWORD = "appSecretPass123"
$DB_NAME = "empdb"
$DB_USER = "db2inst1"
$DB_PORT = "50000"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  DBA TEAM — DB2 Database Setup" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Start DB2 container ---
Write-Host "[1/5] Starting IBM DB2 container (this takes ~2 minutes)..." -ForegroundColor Yellow
docker run -d `
    --name $DB_CONTAINER `
    --privileged `
    -p "${DB_PORT}:${DB_PORT}" `
    -e LICENSE=accept `
    -e DB2INST1_PASSWORD=$DB_PASSWORD `
    -e DBNAME=$DB_NAME `
    -e DB2INSTANCE=$DB_USER `
    icr.io/db2_community/db2:11.5.8.0

# --- Step 2: Wait for DB2 to be ready ---
Write-Host "[2/5] Waiting for DB2 to initialize (checking every 30s)..." -ForegroundColor Yellow
$maxRetries = 20
for ($i = 1; $i -le $maxRetries; $i++) {
    Write-Host "       Attempt $i/$maxRetries..." -ForegroundColor Gray
    $result = docker exec $DB_CONTAINER su - $DB_USER -c "db2 CONNECT TO $DB_NAME" 2>&1
    if ($result -match "Database Connection Information") {
        Write-Host "       DB2 is ready!" -ForegroundColor Green
        break
    }
    if ($i -eq $maxRetries) {
        Write-Host "       ERROR: DB2 did not start in time!" -ForegroundColor Red
        exit 1
    }
    Start-Sleep -Seconds 30
}

# --- Step 3: Create schema and seed data ---
Write-Host "[3/5] Creating employees table and seeding data..." -ForegroundColor Yellow
docker cp (Join-Path $ProjectDir "sql/init.sql") "${DB_CONTAINER}:/tmp/init.sql"
$sqlResult = docker exec $DB_CONTAINER su - $DB_USER -c "db2 CONNECT TO $DB_NAME && db2 -tvf /tmp/init.sql" 2>&1
Write-Host $sqlResult -ForegroundColor Gray

# Verify data
$countResult = docker exec $DB_CONTAINER su - $DB_USER -c "db2 CONNECT TO $DB_NAME && db2 'SELECT COUNT(*) AS TOTAL FROM employees'" 2>&1
Write-Host "       $countResult" -ForegroundColor Gray

# --- Step 4: Read Conjur admin key ---
Write-Host "[4/5] Reading Conjur admin credentials..." -ForegroundColor Yellow
if (-not (Test-Path $KeysFile)) {
    Write-Host "       ERROR: $KeysFile not found. Run 1-conjur-setup.ps1 first!" -ForegroundColor Red
    exit 1
}
$keys = Get-Content $KeysFile | ConvertFrom-StringData
$adminApiKey = $keys.CONJUR_ADMIN_API_KEY
$conjurUrl = $keys.CONJUR_URL
$conjurAccount = $keys.CONJUR_ACCOUNT

# Authenticate to Conjur
$tokenBytes = Invoke-RestMethod -Uri "$conjurUrl/authn/$conjurAccount/admin/authenticate" `
    -Method Post -Body $adminApiKey -ContentType "text/plain"
$token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
$authHeader = @{ Authorization = "Token token=`"$token`"" }

# --- Step 5: Store DB credentials in Conjur ---
Write-Host "[5/5] Storing DB credentials in Conjur vault..." -ForegroundColor Yellow

$secrets = @{
    "db%2Fusername"     = $DB_USER
    "db%2Fpassword"     = $DB_PASSWORD
    "db%2Furl"          = "jdbc:db2://localhost:${DB_PORT}/${DB_NAME}"
    "myapp%2Fapi-key"   = "conjur-api-key-12345"
    "myapp%2Fapp-name"  = "db2-conjur-enterprise"
}

foreach ($secret in $secrets.GetEnumerator()) {
    Invoke-RestMethod -Uri "$conjurUrl/secrets/$conjurAccount/variable/$($secret.Key)" `
        -Method Post -Headers $authHeader -Body $secret.Value -ContentType "text/plain"
    $displayKey = [System.Uri]::UnescapeDataString($secret.Key)
    $displayVal = if ($displayKey -match "password") { "********" } else { $secret.Value }
    Write-Host "       Set $displayKey = $displayVal" -ForegroundColor Gray
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  DB2 setup COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Container:   $DB_CONTAINER"
Write-Host "  Database:    $DB_NAME"
Write-Host "  Port:        $DB_PORT"
Write-Host "  User:        $DB_USER"
Write-Host "  Secrets:     Stored in Conjur vault"
Write-Host ""
Write-Host "  Next: Run .\scripts\3-jenkins-build.ps1" -ForegroundColor Cyan
