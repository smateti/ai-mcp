# NAAG Platform - Seed Data Script
# Creates categories and RAG documents for testing

Add-Type -AssemblyName System.Web

$categoryAdminUrl = "http://localhost:8085"
$ragServiceUrl = "http://localhost:8080"
$toolRegistryUrl = "http://localhost:8083"

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "  NAAG - Seed Data Script" -ForegroundColor Magenta
Write-Host "========================================`n" -ForegroundColor Magenta

# ============================================
# DELETE EXISTING DATA
# ============================================
Write-Host "Cleaning up existing data..." -ForegroundColor Cyan

# Delete all tools
try {
    $null = Invoke-RestMethod -Uri "$toolRegistryUrl/api/tools" -Method Delete -ErrorAction SilentlyContinue
    Write-Host "  [OK] Deleted all tools" -ForegroundColor Green
} catch {
    Write-Host "  [SKIP] No tools to delete or service unavailable" -ForegroundColor Yellow
}

# Delete all RAG documents from Qdrant
try {
    $null = Invoke-RestMethod -Uri "$ragServiceUrl/api/rag/documents" -Method Delete -ErrorAction SilentlyContinue
    Write-Host "  [OK] Deleted all RAG documents from vector store" -ForegroundColor Green
} catch {
    Write-Host "  [SKIP] No RAG documents to delete or service unavailable" -ForegroundColor Yellow
}

# Delete all document uploads
try {
    $null = Invoke-RestMethod -Uri "$ragServiceUrl/api/documents/uploads" -Method Delete -ErrorAction SilentlyContinue
    Write-Host "  [OK] Deleted all document uploads" -ForegroundColor Green
} catch {
    Write-Host "  [SKIP] No document uploads to delete or service unavailable" -ForegroundColor Yellow
}

# ============================================
# CATEGORIES (via Category Admin web forms)
# ============================================
Write-Host "`nCreating Categories..." -ForegroundColor Cyan
Write-Host "  [INFO] Default categories are pre-configured" -ForegroundColor Yellow

# Create additional categories via form POST
$categories = @(
    @{ name = "Batch Development"; description = "Tools and documentation for batch processing, ETL jobs, scheduled tasks, and data pipelines" },
    @{ name = "Service Development"; description = "Tools and documentation for REST APIs, microservices, Spring Boot applications" },
    @{ name = "UI Development"; description = "Tools and documentation for frontend development, React, Angular, Vue.js" },
    @{ name = "Utility Development"; description = "Tools for databases (PostgreSQL, MySQL), Elasticsearch, Redis, and utilities" }
)

foreach ($cat in $categories) {
    try {
        # Use form POST to create category
        $formData = "name=$([System.Web.HttpUtility]::UrlEncode($cat.name))&description=$([System.Web.HttpUtility]::UrlEncode($cat.description))&active=true"

        $null = Invoke-WebRequest -Uri "$categoryAdminUrl/categories" -Method Post `
            -Body $formData `
            -ContentType "application/x-www-form-urlencoded" `
            -MaximumRedirection 0 `
            -ErrorAction SilentlyContinue

        Write-Host "  [OK] Created category: $($cat.name)" -ForegroundColor Green
    } catch {
        if ($_.Exception.Response.StatusCode -eq 302) {
            Write-Host "  [OK] Created category: $($cat.name)" -ForegroundColor Green
        } else {
            Write-Host "  [ERROR] Failed to create category: $($cat.name) - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# ============================================
# RAG DOCUMENTS
# ============================================
Write-Host "`nIngesting RAG Documents..." -ForegroundColor Cyan

$docsDir = "d:\apps\ws\ws8\scripts\seed-docs"

# Document definitions with category mapping
# Note: docId is now auto-generated as sequential IDs starting from 10000
# We use title as the unique identifier for documents
$ragDocs = @(
    @{ title = "Batch Processing Best Practices"; categories = @("Batch Development"); file = "batch-best-practices.md" },
    @{ title = "Spring Batch Development Guide"; categories = @("Batch Development"); file = "spring-batch-guide.md" },
    @{ title = "REST API Design Standards"; categories = @("Service Development"); file = "rest-api-standards.md" },
    @{ title = "Microservices Architecture Patterns"; categories = @("Service Development"); file = "microservices-patterns.md" },
    @{ title = "Spring Boot Security Configuration"; categories = @("Service Development"); file = "spring-boot-security.md" },
    @{ title = "React Development Best Practices"; categories = @("UI Development"); file = "react-best-practices.md" },
    @{ title = "CSS Architecture Guidelines"; categories = @("UI Development"); file = "css-architecture.md" },
    @{ title = "PostgreSQL Development Guide"; categories = @("Utility Development"); file = "postgresql-guide.md" },
    @{ title = "Elasticsearch Development Guide"; categories = @("Utility Development"); file = "elasticsearch-guide.md" },
    @{ title = "Redis Development Guide"; categories = @("Utility Development"); file = "redis-guide.md" }
)

# Generate a simple docId from title for backward compatibility with the /api/rag/ingest endpoint
function Get-DocIdFromTitle {
    param([string]$title)
    return $title.ToLower() -replace '[^a-zA-Z0-9\s]', '' -replace '\s+', '-' -replace '-+', '-' -replace '^-|-$', ''
}

foreach ($doc in $ragDocs) {
    $filePath = Join-Path $docsDir $doc.file
    if (Test-Path $filePath) {
        try {
            $content = Get-Content $filePath -Raw -Encoding UTF8
            # Escape special characters for JSON
            $content = $content -replace '\\', '\\\\' -replace '"', '\"' -replace "`r`n", '\n' -replace "`n", '\n' -replace "`t", '\t'

            # Generate docId from title for backward compatibility
            $docId = Get-DocIdFromTitle -title $doc.title

            $jsonBody = @"
{"docId":"$docId","text":"$content","categories":["$($doc.categories -join '","')"]}
"@

            $null = Invoke-RestMethod -Uri "$ragServiceUrl/api/rag/ingest" -Method Post -Body $jsonBody -ContentType "application/json; charset=utf-8" -ErrorAction Stop
            Write-Host "  [OK] Ingested document: $($doc.title)" -ForegroundColor Green
        } catch {
            Write-Host "  [ERROR] Failed to ingest document: $($doc.title) - $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "  [SKIP] Document file not found: $($doc.file)" -ForegroundColor Yellow
    }
}

# ============================================
# SUMMARY
# ============================================
Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "  Seed Data Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Magenta
Write-Host "`nCreated:"
Write-Host "  - 4 Additional Categories" -ForegroundColor Cyan
Write-Host "  - 10 RAG Documents" -ForegroundColor Cyan
Write-Host "`nNote: Tools are registered via OpenAPI specs"
Write-Host "      Use the Admin UI to register tools from OpenAPI endpoints"
Write-Host "      Tool IDs are now auto-generated as sequential numbers (10000+)"
Write-Host "      Document IDs are also auto-generated when using the upload API"
Write-Host "`nAccess the admin interface at: http://localhost:8085"
Write-Host "Access the chat interface at: http://localhost:8087"
Write-Host ""
