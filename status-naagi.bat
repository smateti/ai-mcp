@echo off
REM NAAG Platform Status Script
REM Nimbus AI Agent - Check service status

powershell -ExecutionPolicy Bypass -File "%~dp0start-naagi.ps1" -Status
