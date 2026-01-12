@echo off
REM Test script for RAG REST API (Windows batch version)
REM This script tests all endpoints of the RAG REST API

setlocal enabledelayedexpansion
set BASE_URL=http://localhost:8080

echo ==========================================
echo RAG REST API Test Suite
echo ==========================================
echo.

REM Test 1: Health Check
echo Test 1: Health Check
echo GET %BASE_URL%/api/rag/health
curl -s -X GET "%BASE_URL%/api/rag/health"
echo.
echo.

REM Test 2: Check OpenAPI spec is accessible
echo Test 2: OpenAPI Specification
echo GET %BASE_URL%/openapi.yaml
curl -s -X GET "%BASE_URL%/openapi.yaml" | findstr /C:"openapi"
echo ...
echo.
echo.

REM Test 3: Ingest a test document
echo Test 3: Ingest Document
echo POST %BASE_URL%/api/rag/ingest
curl -s -X POST "%BASE_URL%/api/rag/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"docId\":\"test-doc-capitals\",\"text\":\"Paris is the capital of France. It is famous for the Eiffel Tower and the Louvre Museum. France is located in Western Europe. London is the capital of the United Kingdom. The UK is known for Big Ben and Buckingham Palace. Berlin is the capital of Germany, known for the Brandenburg Gate.\"}"
echo.
echo.

REM Wait a moment for indexing
echo Waiting 3 seconds for indexing...
timeout /t 3 /nobreak > nul

REM Test 4: Query the ingested document
echo Test 4: Query Document (What is the capital of France?)
echo POST %BASE_URL%/api/rag/query
curl -s -X POST "%BASE_URL%/api/rag/query" ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"What is the capital of France?\",\"topK\":3}"
echo.
echo.

REM Test 5: Query with different question
echo Test 5: Query Document (What is Berlin known for?)
echo POST %BASE_URL%/api/rag/query
curl -s -X POST "%BASE_URL%/api/rag/query" ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"What is Berlin known for?\",\"topK\":5}"
echo.
echo.

REM Test 6: Test validation - empty question
echo Test 6: Validation Test (empty question)
echo POST %BASE_URL%/api/rag/query
curl -s -X POST "%BASE_URL%/api/rag/query" ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"\"}"
echo.
echo.

REM Test 7: Test validation - invalid topK
echo Test 7: Validation Test (invalid topK)
echo POST %BASE_URL%/api/rag/query
curl -s -X POST "%BASE_URL%/api/rag/query" ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"test question\",\"topK\":100}"
echo.
echo.

echo ==========================================
echo Test Suite Complete
echo ==========================================

endlocal
