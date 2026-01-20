# NAAG Platform - Seed Data Script
# Creates categories and RAG documents for testing

Add-Type -AssemblyName System.Web

$categoryAdminUrl = "http://localhost:8085"
$ragServiceUrl = "http://localhost:8080"

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "  NAAG - Seed Data Script" -ForegroundColor Magenta
Write-Host "========================================`n" -ForegroundColor Magenta

# ============================================
# CATEGORIES (via Category Admin web forms)
# ============================================
Write-Host "Creating Categories..." -ForegroundColor Cyan
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
$ragDocs = @(
    @{ docId = "batch-best-practices"; categories = @("Batch Development"); title = "Batch Processing Best Practices"; file = "batch-best-practices.md" },
    @{ docId = "spring-batch-guide"; categories = @("Batch Development"); title = "Spring Batch Development Guide"; file = "spring-batch-guide.md" },
    @{ docId = "rest-api-standards"; categories = @("Service Development"); title = "REST API Design Standards"; file = "rest-api-standards.md" },
    @{ docId = "microservices-patterns"; categories = @("Service Development"); title = "Microservices Architecture Patterns"; file = "microservices-patterns.md" },
    @{ docId = "spring-boot-security"; categories = @("Service Development"); title = "Spring Boot Security Configuration"; file = "spring-boot-security.md" },
    @{ docId = "react-best-practices"; categories = @("UI Development"); title = "React Development Best Practices"; file = "react-best-practices.md" },
    @{ docId = "css-architecture"; categories = @("UI Development"); title = "CSS Architecture Guidelines"; file = "css-architecture.md" },
    @{ docId = "postgresql-guide"; categories = @("Utility Development"); title = "PostgreSQL Development Guide"; file = "postgresql-guide.md" },
    @{ docId = "elasticsearch-guide"; categories = @("Utility Development"); title = "Elasticsearch Development Guide"; file = "elasticsearch-guide.md" },
    @{ docId = "redis-guide"; categories = @("Utility Development"); title = "Redis Development Guide"; file = "redis-guide.md" }
)

foreach ($doc in $ragDocs) {
    $filePath = Join-Path $docsDir $doc.file
    if (Test-Path $filePath) {
        try {
            $content = Get-Content $filePath -Raw -Encoding UTF8
            # Escape special characters for JSON
            $content = $content -replace '\\', '\\\\' -replace '"', '\"' -replace "`r`n", '\n' -replace "`n", '\n' -replace "`t", '\t'

            $jsonBody = @"
{"docId":"$($doc.docId)","text":"$content","categories":["$($doc.categories -join '","')"]}
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
Write-Host "`nAccess the admin interface at: http://localhost:8085"
Write-Host "Access the chat interface at: http://localhost:8087"
Write-Host ""
