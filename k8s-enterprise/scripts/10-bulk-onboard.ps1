#######################################################################
# 10-bulk-onboard.ps1 — Bulk onboard apps + databases to Conjur
#
# Reads a CSV file with app definitions and calls the bulk onboard API
# on conjur-microprofile-rest. Creates per-app K8s Secrets automatically.
# Saves returned API keys to a secure output file.
#######################################################################

param(
    [Parameter(Mandatory=$false)]
    [string]$CsvFile = "",

    [Parameter(Mandatory=$false)]
    [string]$JsonFile = "",

    [Parameter(Mandatory=$false)]
    [string]$RestApiUrl = "http://localhost:30086"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  OPS — Bulk Onboard to Conjur" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --- Pre-check: Verify REST API is running ---
Write-Host "  Pre-check: Verifying conjur-rest API..." -ForegroundColor Yellow
try {
    $status = Invoke-RestMethod -Uri "$RestApiUrl/api/status" -Method Get -TimeoutSec 5
    Write-Host "  conjur-rest API is healthy" -ForegroundColor Green
} catch {
    Write-Host "  ERROR: conjur-rest not reachable at $RestApiUrl" -ForegroundColor Red
    Write-Host "  Run scripts/7-build-deploy-conjur-rest.ps1 first." -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# --- Build the request body ---
$requestBody = $null

if ($JsonFile -ne "") {
    Write-Host "[1/3] Loading JSON from $JsonFile..." -ForegroundColor Yellow
    $requestBody = Get-Content $JsonFile -Raw | ConvertFrom-Json
} elseif ($CsvFile -ne "") {
    Write-Host "[1/3] Loading CSV from $CsvFile..." -ForegroundColor Yellow
    $csv = Import-Csv $CsvFile
    $apps = @()
    foreach ($row in $csv) {
        $databases = @()
        if ($row.databases) {
            $databases = $row.databases -split ";" | Where-Object { $_ }
        }
        $extraSecrets = @{}
        if ($row.extra_secrets) {
            foreach ($pair in ($row.extra_secrets -split ";")) {
                $kv = $pair -split "=", 2
                if ($kv.Count -eq 2) { $extraSecrets[$kv[0]] = $kv[1] }
            }
        }
        $apps += @{
            name = $row.app_name
            namespace = if ($row.namespace) { $row.namespace } else { "apps" }
            databases = $databases
            extraSecrets = $extraSecrets
        }
    }
    $requestBody = @{ apps = $apps }
} else {
    # Demo mode — onboard sample apps
    Write-Host "[1/3] No input file specified. Using demo apps..." -ForegroundColor Yellow
    $requestBody = @{
        apps = @(
            @{ name = "order-service"; namespace = "apps"; databases = @("orderdb"); extraSecrets = @{} },
            @{ name = "inventory-service"; namespace = "apps"; databases = @("inventorydb"); extraSecrets = @{} },
            @{ name = "payment-service"; namespace = "apps"; databases = @("paymentdb"); extraSecrets = @{} }
        )
    }
}

$json = $requestBody | ConvertTo-Json -Depth 5
$appCount = $requestBody.apps.Count
Write-Host "       $appCount app(s) to onboard" -ForegroundColor Green

# --- Call the bulk onboard API ---
Write-Host "[2/3] Calling POST /api/onboard/full..." -ForegroundColor Yellow
try {
    $result = Invoke-RestMethod -Uri "$RestApiUrl/api/onboard/full" `
        -Method Post -Body $json -ContentType "application/json" -TimeoutSec 60

    Write-Host "       Onboarding complete!" -ForegroundColor Green

    # Display results
    if ($result.databases) {
        Write-Host ""
        Write-Host "  Databases registered: $($result.databases.registered)" -ForegroundColor White
    }

    Write-Host "  Apps onboarded: $($result.totalApps)" -ForegroundColor White
    Write-Host ""

    foreach ($app in $result.apps) {
        $statusColor = if ($app.status -eq "created") { "Green" } else { "Yellow" }
        Write-Host "  $($app.name):" -ForegroundColor Cyan
        Write-Host "    Host ID:    $($app.hostId)"
        Write-Host "    API Key:    $($app.apiKey)" -ForegroundColor DarkYellow
        Write-Host "    K8s Secret: $($app.k8sSecret)"
        Write-Host "    Status:     $($app.status)" -ForegroundColor $statusColor
        Write-Host "    CONJUR_SECRETS: $($app.conjurSecretsMapping)" -ForegroundColor Gray
        Write-Host ""
    }

    if ($result.errors -and $result.errors.Count -gt 0) {
        Write-Host "  Errors:" -ForegroundColor Red
        foreach ($err in $result.errors) {
            Write-Host "    - $err" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "  ERROR: Onboarding failed" -ForegroundColor Red
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# --- Save results ---
Write-Host "[3/3] Saving results..." -ForegroundColor Yellow
$outputFile = Join-Path $ProjectDir "onboarding-results.csv"
$csvOutput = "app_name,host_id,api_key,k8s_secret,status,conjur_secrets_mapping"
foreach ($app in $result.apps) {
    $csvOutput += "`n$($app.name),$($app.hostId),$($app.apiKey),$($app.k8sSecret),$($app.status),$($app.conjurSecretsMapping)"
}
$csvOutput | Out-File -FilePath $outputFile -Encoding UTF8
Write-Host "       Results saved to: $outputFile" -ForegroundColor Green

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Bulk Onboarding COMPLETE" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  IMPORTANT: Save the API keys above!" -ForegroundColor Red
Write-Host "  They cannot be retrieved again." -ForegroundColor Red
Write-Host ""
Write-Host "  Next steps:" -ForegroundColor White
Write-Host "    1. Set DB credentials: PUT /api/secrets/db/{name}" -ForegroundColor Cyan
Write-Host "    2. Generate app YAML:  .\scripts\11-generate-app-yaml.ps1" -ForegroundColor Cyan
Write-Host "    3. Deploy apps to K8s" -ForegroundColor Cyan
