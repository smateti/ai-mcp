@echo off
REM ============================================
REM Install llama.cpp Reranker as Windows Service
REM Requires NSSM (Non-Sucking Service Manager)
REM Download: https://nssm.cc/download
REM ============================================

set SERVICE_NAME=LlamaCppReranker
set NSSM_PATH=D:\apps\nssm\nssm.exe
set LLAMA_SERVER=D:\apps\llama-cpp\llama-server.exe
set MODEL_PATH=C:\Users\smate\.ollama\models\blobs\sha256-4bf51534d8d1aebced4de6eca4a8a39bd207170b42e3dcffa7718d194771a713
set LOG_DIR=D:\apps\llama-cpp\logs

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

REM Check if NSSM exists
if not exist "%NSSM_PATH%" (
    echo.
    echo ERROR: NSSM not found at %NSSM_PATH%
    echo.
    echo Please download NSSM from https://nssm.cc/download
    echo Extract and update NSSM_PATH in this script.
    echo.
    pause
    exit /b 1
)

REM Create log directory
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM Remove existing service if present
"%NSSM_PATH%" stop %SERVICE_NAME% >nul 2>&1
"%NSSM_PATH%" remove %SERVICE_NAME% confirm >nul 2>&1

echo.
echo ============================================
echo Installing %SERVICE_NAME% Service
echo ============================================
echo.

REM Install the service
"%NSSM_PATH%" install %SERVICE_NAME% "%LLAMA_SERVER%"

REM Set service parameters
"%NSSM_PATH%" set %SERVICE_NAME% AppParameters -m "%MODEL_PATH%" --host 0.0.0.0 --port 8001 -c 8192 -ngl 99 --rerank --no-webui

REM Set working directory
"%NSSM_PATH%" set %SERVICE_NAME% AppDirectory "D:\apps\llama-cpp"

REM Configure logging
"%NSSM_PATH%" set %SERVICE_NAME% AppStdout "%LOG_DIR%\reranker-stdout.log"
"%NSSM_PATH%" set %SERVICE_NAME% AppStderr "%LOG_DIR%\reranker-stderr.log"
"%NSSM_PATH%" set %SERVICE_NAME% AppStdoutCreationDisposition 4
"%NSSM_PATH%" set %SERVICE_NAME% AppStderrCreationDisposition 4
"%NSSM_PATH%" set %SERVICE_NAME% AppRotateFiles 1
"%NSSM_PATH%" set %SERVICE_NAME% AppRotateBytes 10485760

REM Set service description
"%NSSM_PATH%" set %SERVICE_NAME% Description "llama.cpp server running BGE Reranker v2 M3 model for RAG re-ranking"
"%NSSM_PATH%" set %SERVICE_NAME% DisplayName "LlamaCpp BGE Reranker Service"

REM Set restart behavior
"%NSSM_PATH%" set %SERVICE_NAME% AppExit Default Restart
"%NSSM_PATH%" set %SERVICE_NAME% AppRestartDelay 5000

REM Start the service
echo Starting service...
"%NSSM_PATH%" start %SERVICE_NAME%

echo.
echo ============================================
echo Service installed successfully!
echo ============================================
echo.
echo Service Name: %SERVICE_NAME%
echo Port: 8001
echo Logs: %LOG_DIR%
echo.
echo Commands:
echo   Start:   nssm start %SERVICE_NAME%
echo   Stop:    nssm stop %SERVICE_NAME%
echo   Status:  nssm status %SERVICE_NAME%
echo   Remove:  nssm remove %SERVICE_NAME%
echo.
echo Or use Windows Services (services.msc)
echo.

pause
