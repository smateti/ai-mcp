@echo off
REM NAAG Platform Stop Script
REM Nimbus AI Agent - Stop all services

powershell -ExecutionPolicy Bypass -File "%~dp0start-naagi.ps1" -StopAll
