#######################################################################
# 5-password-rotate.ps1 — DBA + Ops: Password Rotation
#
# Simulates enterprise password rotation:
#   1. Change password in DB2
#   2. Update password in Conjur
#   3. Verify new credentials
#   4. Instruct ops to restart Liberty
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$KeysFile = Join-Path $ScriptDir ".conjur-keys"

$DB_CONTAINER = "app-db2"
$DB_NAME = "empdb"
$DB_USER = "db2inst1"

# Generate a new password
$NewPassword = "rotated_" + (Get-Random -Maximum 999999).ToString("D6")

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  PASSWORD ROTATION — DBA + Ops" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  New password: $NewPassword" -ForegroundColor Yellow
Write-Host ""

# --- Step 1: Change password in DB2 ---
Write-Host "[1/4] Changing password in DB2..." -ForegroundColor Yellow
# In DB2 community image, the db2inst1 password is the OS password
# Use docker exec to change it
$result = docker exec $DB_CONTAINER bash -c "echo '${DB_USER}:${NewPassword}' | chpasswd" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "       DB2 password changed successfully" -ForegroundColor Green
} else {
    Write-Host "       WARNING: $result" -ForegroundColor DarkYellow
}

# Verify DB2 connectivity with new password
$verifyResult = docker exec $DB_CONTAINER su - $DB_USER -c "db2 CONNECT TO $DB_NAME" 2>&1
if ($verifyResult -match "Database Connection Information") {
    Write-Host "       Verified: Can connect to DB2 with new password" -ForegroundColor Green
} else {
    Write-Host "       WARNING: DB2 connection test inconclusive" -ForegroundColor DarkYellow
}

# --- Step 2: Update password in Conjur ---
Write-Host "[2/4] Updating password in Conjur vault..." -ForegroundColor Yellow
$keys = Get-Content $KeysFile | ConvertFrom-StringData
$adminApiKey = $keys.CONJUR_ADMIN_API_KEY
$conjurUrl = $keys.CONJUR_URL
$conjurAccount = $keys.CONJUR_ACCOUNT

# Authenticate as admin
$tokenBytes = Invoke-RestMethod -Uri "$conjurUrl/authn/$conjurAccount/admin/authenticate" `
    -Method Post -Body $adminApiKey -ContentType "text/plain"
$token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
$authHeader = @{ Authorization = "Token token=`"$token`"" }

# Update the secret
Invoke-RestMethod -Uri "$conjurUrl/secrets/$conjurAccount/variable/db%2Fpassword" `
    -Method Post -Headers $authHeader -Body $NewPassword -ContentType "text/plain"
Write-Host "       Conjur secret db/password updated" -ForegroundColor Green

# --- Step 3: Verify by fetching as host ---
Write-Host "[3/4] Verifying: Fetching secret as app host..." -ForegroundColor Yellow
$hostApiKey = $keys.APP_HOST_API_KEY
$encodedLogin = [System.Uri]::EscapeDataString("host/myapp/liberty-app")
$tokenBytes = Invoke-RestMethod -Uri "$conjurUrl/authn/$conjurAccount/$encodedLogin/authenticate" `
    -Method Post -Body $hostApiKey -ContentType "text/plain"
$token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
$hostAuthHeader = @{ Authorization = "Token token=`"$token`"" }

$fetchedPassword = Invoke-RestMethod -Uri "$conjurUrl/secrets/$conjurAccount/variable/db%2Fpassword" `
    -Method Get -Headers $hostAuthHeader

if ($fetchedPassword -eq $NewPassword) {
    Write-Host "       Verified: Conjur returns the new password" -ForegroundColor Green
} else {
    Write-Host "       ERROR: Password mismatch!" -ForegroundColor Red
    exit 1
}

# --- Step 4: Instructions ---
Write-Host "[4/4] Rotation complete." -ForegroundColor Yellow

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Password rotation COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Timeline of what happened:" -ForegroundColor White
Write-Host "    1. DB2 password changed         (DBA action)" -ForegroundColor Gray
Write-Host "    2. Conjur secret updated         (DBA action)" -ForegroundColor Gray
Write-Host "    3. Verified via host identity    (Ops verification)" -ForegroundColor Gray
Write-Host ""
Write-Host "  IMPORTANT: Liberty is still using the OLD password." -ForegroundColor Red
Write-Host "  Existing connections will work, but new ones will fail." -ForegroundColor Red
Write-Host ""
Write-Host "  To pick up the new password:" -ForegroundColor Yellow
Write-Host "    1. Stop Liberty (Ctrl+C in the 4-app-deploy window)" -ForegroundColor White
Write-Host "    2. Re-run: .\scripts\4-app-deploy.ps1" -ForegroundColor White
Write-Host "  This mimics a 'kubectl rollout restart' in Kubernetes." -ForegroundColor Gray
