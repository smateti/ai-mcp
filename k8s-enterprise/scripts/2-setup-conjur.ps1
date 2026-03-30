#######################################################################
# 2-setup-conjur.ps1 — Security Team: Conjur + PostgreSQL on K8s
#
# Deploys PostgreSQL and Conjur OSS to the conjur-system namespace.
# Creates account, loads policies, sets secrets, creates app identity.
#######################################################################

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$PolicyDir = Join-Path $ProjectDir "employee-app/conjur/policy"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  SECURITY TEAM — Conjur + PostgreSQL Setup" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Step 1: Deploy PostgreSQL ---
Write-Host "[1/7] Deploying PostgreSQL (Conjur backend)..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/conjur/postgres.yaml")
kubectl wait --for=condition=ready pod -l app=conjur-postgres -n conjur-system --timeout=120s
Write-Host "       PostgreSQL is ready" -ForegroundColor Green

# --- Step 2: Deploy Conjur ---
Write-Host "[2/7] Deploying Conjur OSS..." -ForegroundColor Yellow
kubectl apply -f (Join-Path $ProjectDir "k8s/conjur/conjur.yaml")
Write-Host "       Waiting for Conjur to start (this may take ~60s)..." -ForegroundColor Gray
kubectl wait --for=condition=ready pod -l app=conjur-server -n conjur-system --timeout=180s
Write-Host "       Conjur is ready" -ForegroundColor Green

# --- Step 3: Create Conjur account ---
Write-Host "[3/7] Creating Conjur account 'myConjurAccount'..." -ForegroundColor Yellow
$conjurPod = kubectl get pod -l app=conjur-server -n conjur-system -o jsonpath="{.items[0].metadata.name}"
$accountOutput = kubectl exec $conjurPod -n conjur-system -- conjurctl account create myConjurAccount 2>&1
Write-Host "       $accountOutput" -ForegroundColor Gray

# Extract admin API key from output
$adminApiKey = ($accountOutput | Select-String "API key for admin: (.+)" | ForEach-Object { $_.Matches[0].Groups[1].Value }).Trim()
if (-not $adminApiKey) {
    # Account may already exist — try to get the key from the role
    Write-Host "       Account may already exist. Attempting to use existing key..." -ForegroundColor DarkYellow
    $adminApiKey = kubectl exec $conjurPod -n conjur-system -- conjurctl role retrieve-key myConjurAccount:user:admin 2>&1
    $adminApiKey = $adminApiKey.Trim()
}
Write-Host "       Admin API key obtained" -ForegroundColor Green

# --- Step 4: Start port-forward ---
Write-Host "[4/7] Setting up port-forward to Conjur..." -ForegroundColor Yellow
$portForward = Start-Process -NoNewWindow -PassThru -FilePath "kubectl" `
    -ArgumentList "port-forward svc/conjur-server 8080:80 -n conjur-system"
Start-Sleep -Seconds 5
$conjurUrl = "http://localhost:8080"
Write-Host "       Conjur accessible at $conjurUrl" -ForegroundColor Green

try {
    # --- Step 5: Load policies ---
    Write-Host "[5/7] Loading Conjur policies..." -ForegroundColor Yellow

    # Authenticate as admin
    $tokenBytes = Invoke-RestMethod -Uri "$conjurUrl/authn/myConjurAccount/admin/authenticate" `
        -Method Post -Body $adminApiKey -ContentType "text/plain"
    $token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
    $authHeader = @{ Authorization = "Token token=`"$token`"" }

    # Load root policy
    $rootPolicy = Get-Content (Join-Path $PolicyDir "root.yml") -Raw
    Invoke-RestMethod -Uri "$conjurUrl/policies/myConjurAccount/policy/root" `
        -Method Put -Headers $authHeader -Body $rootPolicy -ContentType "application/x-yaml"
    Write-Host "       Loaded root.yml" -ForegroundColor Gray

    # Re-authenticate (token may expire)
    $tokenBytes = Invoke-RestMethod -Uri "$conjurUrl/authn/myConjurAccount/admin/authenticate" `
        -Method Post -Body $adminApiKey -ContentType "text/plain"
    $token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
    $authHeader = @{ Authorization = "Token token=`"$token`"" }

    # Load apps policy (capture host API key)
    $appsPolicy = Get-Content (Join-Path $PolicyDir "apps.yml") -Raw
    $appsResult = Invoke-RestMethod -Uri "$conjurUrl/policies/myConjurAccount/policy/apps" `
        -Method Post -Headers $authHeader -Body $appsPolicy -ContentType "application/x-yaml"
    Write-Host "       Loaded apps.yml" -ForegroundColor Gray

    # Extract host API key
    $hostApiKey = ""
    foreach ($item in $appsResult.created_roles.PSObject.Properties) {
        if ($item.Name -match "host:") {
            $hostApiKey = $item.Value.api_key
        }
    }
    if (-not $hostApiKey) {
        Write-Host "       WARNING: Could not extract host API key (host may already exist)" -ForegroundColor DarkYellow
    } else {
        Write-Host "       Host API key obtained for liberty-app" -ForegroundColor Green
    }

    # Re-authenticate
    $tokenBytes = Invoke-RestMethod -Uri "$conjurUrl/authn/myConjurAccount/admin/authenticate" `
        -Method Post -Body $adminApiKey -ContentType "text/plain"
    $token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
    $authHeader = @{ Authorization = "Token token=`"$token`"" }

    # Load db-secrets policy
    $dbPolicy = Get-Content (Join-Path $PolicyDir "db-secrets.yml") -Raw
    Invoke-RestMethod -Uri "$conjurUrl/policies/myConjurAccount/policy/db" `
        -Method Post -Headers $authHeader -Body $dbPolicy -ContentType "application/x-yaml"
    Write-Host "       Loaded db-secrets.yml" -ForegroundColor Gray

    # --- Step 6: Set secrets ---
    Write-Host "[6/7] Setting secrets in Conjur vault..." -ForegroundColor Yellow

    # Re-authenticate
    $tokenBytes = Invoke-RestMethod -Uri "$conjurUrl/authn/myConjurAccount/admin/authenticate" `
        -Method Post -Body $adminApiKey -ContentType "text/plain"
    $token = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($tokenBytes))
    $authHeader = @{ Authorization = "Token token=`"$token`"" }

    # Set empdb-uid
    Invoke-RestMethod -Uri "$conjurUrl/secrets/myConjurAccount/variable/db%2Fempdb-uid" `
        -Method Post -Headers $authHeader -Body "db2inst1" -ContentType "text/plain"
    Write-Host "       Set db/empdb-uid = db2inst1" -ForegroundColor Gray

    # Set empdb-pwd
    Invoke-RestMethod -Uri "$conjurUrl/secrets/myConjurAccount/variable/db%2Fempdb-pwd" `
        -Method Post -Headers $authHeader -Body "appSecretPass123" -ContentType "text/plain"
    Write-Host "       Set db/empdb-pwd = ********" -ForegroundColor Gray

    # --- Step 7: Create K8s Secret for app identity ---
    Write-Host "[7/7] Creating K8s Secret for app identity..." -ForegroundColor Yellow

    # Delete if exists
    kubectl delete secret conjur-app-identity -n apps --ignore-not-found 2>$null

    kubectl create secret generic conjur-app-identity -n apps `
        --from-literal=CONJUR_APPLIANCE_URL=http://conjur-server.conjur-system.svc.cluster.local `
        --from-literal=CONJUR_ACCOUNT=myConjurAccount `
        --from-literal=CONJUR_AUTHN_LOGIN=host/apps/liberty-app `
        --from-literal=CONJUR_AUTHN_API_KEY=$hostApiKey `
        --from-literal=CONJUR_ADMIN_API_KEY=$adminApiKey `
        --from-literal=DB_USERNAME=db2inst1 `
        --from-literal=DB_PASSWORD=appSecretPass123

    Write-Host "       K8s Secret 'conjur-app-identity' created in 'apps' namespace" -ForegroundColor Green

} finally {
    # Stop port-forward
    if ($portForward -and !$portForward.HasExited) {
        Stop-Process -Id $portForward.Id -Force -ErrorAction SilentlyContinue
        Write-Host "       Port-forward stopped" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Conjur setup COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  PostgreSQL:  conjur-postgres (conjur-system)"
Write-Host "  Conjur:      conjur-server  (conjur-system)"
Write-Host "  NodePort:    http://localhost:30080"
Write-Host "  Account:     myConjurAccount"
Write-Host "  Secrets:     db/empdb-uid, db/empdb-pwd"
Write-Host "  App Secret:  conjur-app-identity (apps namespace)"
Write-Host ""
Write-Host "  Next: Run .\scripts\3-setup-db2.ps1" -ForegroundColor Cyan
