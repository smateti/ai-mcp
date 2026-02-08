# COBOL Analysis Platform - Elasticsearch Index Explorer
# Usage: .\es-explore.ps1

$ES = "http://localhost:9200"

function Show-Menu {
    Write-Host "`n=== Elasticsearch Index Explorer ===" -ForegroundColor Cyan
    Write-Host "1. List all indices (size, doc count)"
    Write-Host "2. Show index mappings"
    Write-Host "3. Browse programs"
    Write-Host "4. Browse paragraphs"
    Write-Host "5. Browse dependencies"
    Write-Host "6. Search programs by name"
    Write-Host "7. Show business rules for a program"
    Write-Host "8. Show business summary for a program"
    Write-Host "9. Show all programs with business rules count"
    Write-Host "10. Show dependency graph for a program"
    Write-Host "11. Stats overview (counts, types)"
    Write-Host "12. Raw query (custom)"
    Write-Host "0. Exit"
    Write-Host ""
}

function Invoke-ES {
    param([string]$Path, [string]$Method = "GET", [string]$Body = $null)
    $params = @{ Uri = "$ES$Path"; Method = $Method; ContentType = "application/json" }
    if ($Body) { $params.Body = $Body }
    try {
        $response = Invoke-RestMethod @params
        return $response
    } catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

function Show-Indices {
    Write-Host "`n--- All Indices ---" -ForegroundColor Yellow
    $result = Invoke-ES "/_cat/indices?v&s=index&format=json"
    if ($result) {
        $result | Format-Table @{L="Index";E={$_.index}},
                               @{L="Health";E={$_.health}},
                               @{L="Status";E={$_.status}},
                               @{L="Docs";E={$_.'docs.count'}},
                               @{L="Size";E={$_.'store.size'}} -AutoSize
    }
}

function Show-Mappings {
    $index = Read-Host "Index name (cobol-programs, cobol-paragraphs, cobol-dependencies)"
    $result = Invoke-ES "/$index/_mapping"
    if ($result) {
        $result | ConvertTo-Json -Depth 10 | Write-Host
    }
}

function Browse-Programs {
    $size = Read-Host "How many programs to show (default 10)"
    if (-not $size) { $size = 10 }
    $body = @{
        size = [int]$size
        _source = @("programName","programType","author","lineCount","paragraphCount","usesCics","usesDb2","batchRunId")
        sort = @(@{programName = "asc"})
        query = @{match_all = @{}}
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-programs/_search" "POST" $body
    if ($result) {
        Write-Host "`n--- Programs ($($result.hits.total.value) total) ---" -ForegroundColor Yellow
        $result.hits.hits | ForEach-Object {
            $s = $_._source
            $cics = if ($s.usesCics) { "CICS" } else { "" }
            $db2 = if ($s.usesDb2) { "DB2" } else { "" }
            Write-Host ("{0,-15} {1,-8} {2,-6} {3,5} lines  {4,3} paras  {5} {6}" -f $s.programName, $s.programType, $s.author, $s.lineCount, $s.paragraphCount, $cics, $db2)
        }
    }
}

function Browse-Paragraphs {
    $program = Read-Host "Program name (e.g. CBTRN02C, or * for all)"
    $size = Read-Host "How many (default 20)"
    if (-not $size) { $size = 20 }
    $query = if ($program -eq "*") { @{match_all = @{}} } else { @{term = @{programId = $program}} }
    $body = @{
        size = [int]$size
        _source = @("paragraphName","programId","lineCount","businessDescription")
        sort = @(@{programId = "asc"}, @{paragraphName = "asc"})
        query = $query
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-paragraphs/_search" "POST" $body
    if ($result) {
        Write-Host "`n--- Paragraphs ($($result.hits.total.value) total) ---" -ForegroundColor Yellow
        $result.hits.hits | ForEach-Object {
            $s = $_._source
            $desc = if ($s.businessDescription) { $s.businessDescription.Substring(0, [Math]::Min(80, $s.businessDescription.Length)) + "..." } else { "(not analyzed)" }
            Write-Host ("{0,-15} {1,-30} {2}" -f $s.programId, $s.paragraphName, $desc)
        }
    }
}

function Browse-Dependencies {
    $program = Read-Host "Program name (e.g. CBTRN02C, or * for all)"
    $size = Read-Host "How many (default 50)"
    if (-not $size) { $size = 50 }
    $query = if ($program -eq "*") { @{match_all = @{}} } else { @{term = @{sourceProgram = $program}} }
    $body = @{
        size = [int]$size
        _source = @("sourceProgram","targetName","type","context")
        sort = @(@{sourceProgram = "asc"}, @{type = "asc"})
        query = $query
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-dependencies/_search" "POST" $body
    if ($result) {
        Write-Host "`n--- Dependencies ($($result.hits.total.value) total) ---" -ForegroundColor Yellow
        $result.hits.hits | ForEach-Object {
            $s = $_._source
            Write-Host ("{0,-15} --{1,-6}--> {2,-20} {3}" -f $s.sourceProgram, $s.type, $s.targetName, $s.context)
        }
    }
}

function Search-Programs {
    $term = Read-Host "Search term"
    $body = @{
        size = 20
        _source = @("programName","programType","lineCount","paragraphCount")
        query = @{
            wildcard = @{
                programName = @{ value = "*$($term.ToUpper())*" }
            }
        }
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-programs/_search" "POST" $body
    if ($result) {
        Write-Host "`n--- Search Results ($($result.hits.total.value) matches) ---" -ForegroundColor Yellow
        $result.hits.hits | ForEach-Object {
            $s = $_._source
            Write-Host ("{0,-15} {1,-8} {2,5} lines  {3,3} paras" -f $s.programName, $s.programType, $s.lineCount, $s.paragraphCount)
        }
    }
}

function Show-BusinessRules {
    $program = Read-Host "Program name (e.g. CBACT01C)"
    $result = Invoke-ES "/cobol-programs/_doc/$($program)?_source=programName,extractedBusinessRules"
    if ($result -and $result.found) {
        $rules = $result._source.extractedBusinessRules
        Write-Host "`n--- Business Rules for $program ($($rules.Count) rules) ---" -ForegroundColor Yellow
        if ($rules.Count -eq 0) {
            Write-Host "  (no business rules extracted)" -ForegroundColor DarkGray
        } else {
            $rules | ForEach-Object {
                if ($_ -match '^\[(\w+)\]\s*(.+)') {
                    $cat = $Matches[1]
                    $desc = $Matches[2]
                    $color = switch ($cat) {
                        "VALIDATION" { "Green" }
                        "CALCULATION" { "Cyan" }
                        "LIMIT_CHECK" { "Yellow" }
                        "ELIGIBILITY" { "Magenta" }
                        "BUSINESS_DECISION" { "White" }
                        "STATUS_TRANSITION" { "DarkYellow" }
                        "THRESHOLD" { "Red" }
                        default { "Gray" }
                    }
                    Write-Host "  [$cat] " -ForegroundColor $color -NoNewline
                    Write-Host $desc
                } else {
                    Write-Host "  $_"
                }
            }
        }
    } else {
        Write-Host "Program '$program' not found" -ForegroundColor Red
    }
}

function Show-BusinessSummary {
    $program = Read-Host "Program name (e.g. CBACT01C)"
    $result = Invoke-ES "/cobol-programs/_doc/$($program)?_source=programName,programType,businessSummary"
    if ($result -and $result.found) {
        $s = $result._source
        Write-Host "`n--- Business Summary: $($s.programName) ($($s.programType)) ---" -ForegroundColor Yellow
        if ($s.businessSummary) {
            Write-Host $s.businessSummary
        } else {
            Write-Host "  (not analyzed yet)" -ForegroundColor DarkGray
        }
    } else {
        Write-Host "Program '$program' not found" -ForegroundColor Red
    }
}

function Show-AllBusinessRules {
    $body = @{
        size = 50
        _source = @("programName","programType","extractedBusinessRules")
        sort = @(@{programName = "asc"})
        query = @{match_all = @{}}
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-programs/_search" "POST" $body
    if ($result) {
        Write-Host "`n--- All Programs - Business Rules Count ---" -ForegroundColor Yellow
        $totalRules = 0
        $result.hits.hits | ForEach-Object {
            $s = $_._source
            $count = if ($s.extractedBusinessRules) { $s.extractedBusinessRules.Count } else { 0 }
            $totalRules += $count
            $color = if ($count -gt 0) { "Green" } else { "DarkGray" }
            Write-Host ("{0,-15} {1,-8} {2,3} rules" -f $s.programName, $s.programType, $count) -ForegroundColor $color
        }
        Write-Host "`nTotal: $totalRules rules across $($result.hits.total.value) programs" -ForegroundColor Cyan
    }
}

function Show-DependencyGraph {
    $program = Read-Host "Program name (e.g. CBTRN02C)"
    $body = @{
        size = 200
        _source = @("sourceProgram","targetName","type")
        query = @{
            bool = @{
                should = @(
                    @{term = @{sourceProgram = $program}},
                    @{term = @{targetName = $program}}
                )
            }
        }
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-dependencies/_search" "POST" $body
    if ($result) {
        $outgoing = $result.hits.hits | Where-Object { $_._source.sourceProgram -eq $program }
        $incoming = $result.hits.hits | Where-Object { $_._source.targetName -eq $program }
        Write-Host "`n--- Dependency Graph: $program ---" -ForegroundColor Yellow
        if ($outgoing) {
            Write-Host "`n  Calls / Uses:" -ForegroundColor Cyan
            $outgoing | Group-Object { $_._source.type } | ForEach-Object {
                Write-Host "    $($_.Name):" -ForegroundColor DarkCyan
                $_.Group | ForEach-Object {
                    Write-Host "      -> $($_._source.targetName)"
                }
            }
        }
        if ($incoming) {
            Write-Host "`n  Called By / Used By:" -ForegroundColor Magenta
            $incoming | Group-Object { $_._source.type } | ForEach-Object {
                Write-Host "    $($_.Name):" -ForegroundColor DarkMagenta
                $_.Group | ForEach-Object {
                    Write-Host "      <- $($_._source.sourceProgram)"
                }
            }
        }
        if (-not $outgoing -and -not $incoming) {
            Write-Host "  No dependencies found" -ForegroundColor DarkGray
        }
    }
}

function Show-Stats {
    Write-Host "`n--- Stats Overview ---" -ForegroundColor Yellow

    # Counts
    $progCount = (Invoke-ES "/cobol-programs/_count").count
    $paraCount = (Invoke-ES "/cobol-paragraphs/_count").count
    $depCount = (Invoke-ES "/cobol-dependencies/_count").count
    Write-Host "  Programs:     $progCount"
    Write-Host "  Paragraphs:   $paraCount"
    Write-Host "  Dependencies: $depCount"

    # Program types
    $body = @{
        size = 0
        aggs = @{
            by_type = @{ terms = @{ field = "programType"; size = 10 } }
        }
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-programs/_search" "POST" $body
    if ($result) {
        Write-Host "`n  Program Types:" -ForegroundColor Cyan
        $result.aggregations.by_type.buckets | ForEach-Object {
            Write-Host "    $($_.key): $($_.doc_count)"
        }
    }

    # Dependency types
    $body = @{
        size = 0
        aggs = @{
            by_type = @{ terms = @{ field = "type"; size = 10 } }
        }
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-dependencies/_search" "POST" $body
    if ($result) {
        Write-Host "`n  Dependency Types:" -ForegroundColor Cyan
        $result.aggregations.by_type.buckets | ForEach-Object {
            Write-Host "    $($_.key): $($_.doc_count)"
        }
    }

    # Analyzed count
    $body = @{
        size = 0
        query = @{ exists = @{ field = "businessSummary" } }
    } | ConvertTo-Json -Depth 5
    $result = Invoke-ES "/cobol-programs/_search" "POST" $body
    $analyzed = $result.hits.total.value
    Write-Host "`n  Analyzed:     $analyzed / $progCount programs" -ForegroundColor $(if ($analyzed -eq $progCount) { "Green" } else { "Yellow" })
}

function Run-RawQuery {
    $index = Read-Host "Index (cobol-programs, cobol-paragraphs, cobol-dependencies)"
    Write-Host "Enter JSON query body (single line):"
    $body = Read-Host
    $result = Invoke-ES "/$index/_search" "POST" $body
    if ($result) {
        $result | ConvertTo-Json -Depth 15 | Write-Host
    }
}

# Main loop
while ($true) {
    Show-Menu
    $choice = Read-Host "Choice"
    switch ($choice) {
        "1"  { Show-Indices }
        "2"  { Show-Mappings }
        "3"  { Browse-Programs }
        "4"  { Browse-Paragraphs }
        "5"  { Browse-Dependencies }
        "6"  { Search-Programs }
        "7"  { Show-BusinessRules }
        "8"  { Show-BusinessSummary }
        "9"  { Show-AllBusinessRules }
        "10" { Show-DependencyGraph }
        "11" { Show-Stats }
        "12" { Run-RawQuery }
        "0"  { Write-Host "Bye!" -ForegroundColor Cyan; exit }
        default { Write-Host "Invalid choice" -ForegroundColor Red }
    }
}
