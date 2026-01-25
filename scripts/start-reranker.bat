@echo off
REM ============================================
REM Start llama.cpp server with BGE Reranker v2
REM ============================================

set LLAMA_SERVER=D:\apps\llama-cpp\llama-server.exe
set MODEL_PATH=C:\Users\smate\.ollama\models\blobs\sha256-4bf51534d8d1aebced4de6eca4a8a39bd207170b42e3dcffa7718d194771a713
set PORT=8001
set HOST=0.0.0.0
set CONTEXT_SIZE=8192
set GPU_LAYERS=99

REM Check if model exists
if not exist "%MODEL_PATH%" (
    echo.
    echo ERROR: Model not found at %MODEL_PATH%
    echo.
    echo Make sure you have pulled the model in Ollama:
    echo   ollama pull qllama/bge-reranker-v2-m3
    echo.
    pause
    exit /b 1
)

REM Check if llama-server exists
if not exist "%LLAMA_SERVER%" (
    echo.
    echo ERROR: llama-server not found at %LLAMA_SERVER%
    echo Please update the LLAMA_SERVER path in this script.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================
echo Starting BGE Reranker v2 M3 Server
echo ============================================
echo Model: %MODEL_PATH%
echo Port: %PORT%
echo Context: %CONTEXT_SIZE%
echo GPU Layers: %GPU_LAYERS%
echo ============================================
echo.

"%LLAMA_SERVER%" ^
    -m "%MODEL_PATH%" ^
    --host %HOST% ^
    --port %PORT% ^
    -c %CONTEXT_SIZE% ^
    -ngl %GPU_LAYERS% ^
    --rerank ^
    --no-webui

pause
