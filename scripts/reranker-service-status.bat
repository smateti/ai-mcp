@echo off
REM ============================================
REM Check llama.cpp Reranker Service Status
REM ============================================

set SERVICE_NAME=LlamaCppReranker
set NSSM_PATH=D:\apps\nssm\nssm.exe

echo.
echo ============================================
echo %SERVICE_NAME% Service Status
echo ============================================
echo.

REM Check NSSM status
"%NSSM_PATH%" status %SERVICE_NAME%

echo.
echo ============================================
echo Health Check (Port 8001)
echo ============================================
echo.

curl -s http://localhost:8001/health 2>nul
if %errorLevel% neq 0 (
    echo Service is not responding on port 8001
) else (
    echo.
)

echo.
pause
