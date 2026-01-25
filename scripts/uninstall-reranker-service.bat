@echo off
REM ============================================
REM Uninstall llama.cpp Reranker Windows Service
REM ============================================

set SERVICE_NAME=LlamaCppReranker
set NSSM_PATH=D:\apps\nssm\nssm.exe

REM Check for admin rights
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo.
    echo ERROR: This script requires Administrator privileges.
    echo Please right-click and select "Run as administrator"
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================
echo Uninstalling %SERVICE_NAME% Service
echo ============================================
echo.

REM Stop the service
echo Stopping service...
"%NSSM_PATH%" stop %SERVICE_NAME% >nul 2>&1

REM Remove the service
echo Removing service...
"%NSSM_PATH%" remove %SERVICE_NAME% confirm

echo.
echo Service removed successfully!
echo.

pause
