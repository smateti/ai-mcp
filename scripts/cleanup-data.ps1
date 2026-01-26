# NAAG Platform - Cleanup Data Script
# Deletes all tools and documents from the system

$categoryAdminUrl = "http://localhost:8085"
$ragServiceUrl = "http://localhost:8080"
$toolRegistryUrl = "http://localhost:8083"

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "  NAAG - Cleanup Data Script" -ForegroundColor Magenta
Write-Host "========================================`n" -ForegroundColor Magenta

# ============================================
# DELETE ALL TOOLS
# ============================================
Write-Host "Deleting all tools..." -ForegroundColor Cyan

try {
    $response = Invoke-RestMethod -Uri "$toolRegistryUrl/api/tools" -Method Delete -ErrorAction Stop
    if ($response.success) {
        Write-Host "  [OK] Deleted $($response.deletedCount) tools" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] $($response.message)" -ForegroundColor Yellow
    }
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "  [OK] No tools to delete" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] Failed to delete tools: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ============================================
# DELETE ALL RAG DOCUMENTS (from Qdrant)
# ============================================
Write-Host "`nDeleting all RAG documents from vector store..." -ForegroundColor Cyan

try {
    $response = Invoke-RestMethod -Uri "$ragServiceUrl/api/rag/documents" -Method Delete -ErrorAction Stop
    if ($response.success) {
        Write-Host "  [OK] $($response.message)" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] $($response.error)" -ForegroundColor Yellow
    }
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "  [OK] No RAG documents to delete" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] Failed to delete RAG documents: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ============================================
# DELETE ALL DOCUMENT UPLOADS (from database)
# ============================================
Write-Host "`nDeleting all document uploads from database..." -ForegroundColor Cyan

try {
    $response = Invoke-RestMethod -Uri "$ragServiceUrl/api/documents/uploads" -Method Delete -ErrorAction Stop
    if ($response.success) {
        Write-Host "  [OK] $($response.message)" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] $($response.error)" -ForegroundColor Yellow
    }
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "  [OK] No document uploads to delete" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] Failed to delete document uploads: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ============================================
# SUMMARY
# ============================================
Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "  Cleanup Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Magenta
Write-Host "`nDeleted:"
Write-Host "  - All tools from tool-registry" -ForegroundColor Cyan
Write-Host "  - All documents from Qdrant vector store" -ForegroundColor Cyan
Write-Host "  - All document uploads from database" -ForegroundColor Cyan
Write-Host "`nNote: Categories are preserved (they are configuration, not data)"
Write-Host ""
