# COBOL Analysis Platform - Elasticsearch Index Setup
# Creates the 3 indices: cobol-programs, cobol-paragraphs, cobol-dependencies
#
# Usage:
#   .\es-create-indices.ps1              # Create indices (skip if exist)
#   .\es-create-indices.ps1 -Force       # Delete and recreate
#   .\es-create-indices.ps1 -DeleteOnly  # Just delete all indices

param(
    [string]$EsUrl = "http://localhost:9200",
    [switch]$Force,
    [switch]$DeleteOnly
)

$ErrorActionPreference = "Stop"

function Invoke-ES {
    param([string]$Method, [string]$Path, [string]$Body = $null)
    $params = @{
        Uri = "$EsUrl$Path"
        Method = $Method
        ContentType = "application/json"
    }
    if ($Body) { $params.Body = [System.Text.Encoding]::UTF8.GetBytes($Body) }
    try {
        return Invoke-RestMethod @params
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status -eq 404) { return $null }
        throw
    }
}

function Test-IndexExists([string]$Index) {
    try {
        Invoke-RestMethod -Uri "$EsUrl/$Index" -Method HEAD -ErrorAction Stop | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Remove-Index([string]$Index) {
    if (Test-IndexExists $Index) {
        Write-Host "  Deleting $Index..." -ForegroundColor Yellow -NoNewline
        Invoke-ES "DELETE" "/$Index" | Out-Null
        Write-Host " done" -ForegroundColor Green
    } else {
        Write-Host "  $Index does not exist, skipping delete" -ForegroundColor DarkGray
    }
}

function New-Index([string]$Index, [string]$Mapping) {
    if (Test-IndexExists $Index) {
        if (-not $Force) {
            Write-Host "  $Index already exists (use -Force to recreate)" -ForegroundColor DarkGray
            return
        }
        Remove-Index $Index
    }
    Write-Host "  Creating $Index..." -ForegroundColor Cyan -NoNewline
    Invoke-ES "PUT" "/$Index" $Mapping | Out-Null
    Write-Host " done" -ForegroundColor Green
}

# ─── Check ES connectivity ───

Write-Host "`nElasticsearch Index Setup" -ForegroundColor Cyan
Write-Host "========================" -ForegroundColor Cyan
Write-Host "Target: $EsUrl`n"

try {
    $info = Invoke-RestMethod -Uri $EsUrl -Method GET -TimeoutSec 5
    Write-Host "Connected to ES $($info.version.number)" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Cannot connect to Elasticsearch at $EsUrl" -ForegroundColor Red
    Write-Host "Make sure ES is running: docker start elasticsearch (or similar)" -ForegroundColor Yellow
    exit 1
}

# ─── Delete only mode ───

if ($DeleteOnly) {
    Write-Host "`nDeleting all indices..." -ForegroundColor Yellow
    Remove-Index "cobol-programs"
    Remove-Index "cobol-paragraphs"
    Remove-Index "cobol-dependencies"
    Write-Host "`nAll indices deleted." -ForegroundColor Green
    exit 0
}

# ─── Index definitions ───

$programsMapping = @'
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  },
  "mappings": {
    "properties": {
      "programName":            { "type": "keyword" },
      "programType":            { "type": "keyword" },
      "author":                 { "type": "keyword" },
      "lineCount":              { "type": "integer" },
      "paragraphCount":         { "type": "integer" },
      "usesCics":               { "type": "boolean" },
      "usesDb2":                { "type": "boolean" },
      "usesIdms":               { "type": "boolean" },
      "usesIms":                { "type": "boolean" },
      "usesMq":                 { "type": "boolean" },
      "calledPrograms":         { "type": "keyword" },
      "copybooks":              { "type": "keyword" },
      "businessSummary":        { "type": "text" },
      "sourceCode":             { "type": "text", "index": false },
      "dataStructures":         { "type": "text" },
      "sqlStatements":          { "type": "text" },
      "conditionNames":         { "type": "text" },
      "fileOperations":         { "type": "text" },
      "extractedBusinessRules": { "type": "text" },
      "analyzedAt":             { "type": "date", "format": "uuuu-MM-dd'T'HH:mm:ss.SSS" },
      "batchRunId":             { "type": "keyword" },
      "projectId":              { "type": "long" }
    }
  }
}
'@

$paragraphsMapping = @'
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  },
  "mappings": {
    "properties": {
      "programId":              { "type": "keyword" },
      "programName":            { "type": "keyword" },
      "paragraphName":          { "type": "keyword" },
      "type":                   { "type": "keyword" },
      "sourceCode":             { "type": "text", "index": false },
      "businessSummary":        { "type": "text" },
      "startLine":              { "type": "integer" },
      "endLine":                { "type": "integer" },
      "lineCount":              { "type": "integer" },
      "performsCalls":          { "type": "keyword" },
      "hasExecSql":             { "type": "boolean" },
      "hasExecCics":            { "type": "boolean" },
      "hasCallStatement":       { "type": "boolean" },
      "businessRules":          { "type": "text" },
      "dataAccess":             { "type": "text" },
      "calculations":           { "type": "text" },
      "analyzedAt":             { "type": "date", "format": "uuuu-MM-dd'T'HH:mm:ss.SSS" },
      "batchRunId":             { "type": "keyword" },
      "projectId":              { "type": "long" }
    }
  }
}
'@

$dependenciesMapping = @'
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  },
  "mappings": {
    "properties": {
      "programId":              { "type": "keyword" },
      "programName":            { "type": "keyword" },
      "dependencyType":         { "type": "keyword" },
      "targetName":             { "type": "keyword" },
      "details":                { "type": "object" },
      "callingContext":         { "type": "keyword" },
      "analyzedAt":             { "type": "date", "format": "uuuu-MM-dd'T'HH:mm:ss.SSS" },
      "batchRunId":             { "type": "keyword" },
      "projectId":              { "type": "long" }
    }
  }
}
'@

# ─── Create indices ───

Write-Host "`nCreating indices..." -ForegroundColor Cyan
New-Index "cobol-programs"     $programsMapping
New-Index "cobol-paragraphs"   $paragraphsMapping
New-Index "cobol-dependencies" $dependenciesMapping

# ─── Verify ───

Write-Host "`nVerification:" -ForegroundColor Cyan
$indices = Invoke-RestMethod -Uri "$EsUrl/_cat/indices/cobol-*?v&s=index&format=json"
$indices | ForEach-Object {
    $status = if ($_.health -eq "green" -or $_.health -eq "yellow") { "Green" } else { "Red" }
    Write-Host ("  {0,-25} {1,-8} {2,-8} docs:{3}" -f $_.index, $_.health, $_.status, $_.'docs.count') -ForegroundColor $status
}

Write-Host "`nDone!" -ForegroundColor Green
