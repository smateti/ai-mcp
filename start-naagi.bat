@echo off
REM NAAG Platform Startup Script (Batch wrapper)
REM Nimbus AI Agent - Start all services

powershell -ExecutionPolicy Bypass -File "%~dp0start-naagi.ps1" %*
