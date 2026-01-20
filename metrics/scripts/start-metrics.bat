@echo off
REM Start Prometheus and Grafana for NAAG Platform metrics

echo Starting NAAG Metrics Stack...
echo ================================

cd /d "%~dp0\.."

REM Check if Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo Error: Docker is not running. Please start Docker first.
    exit /b 1
)

REM Start the services
docker-compose up -d

echo.
echo Metrics stack started successfully!
echo.
echo Access points:
echo   - Prometheus: http://localhost:9090
echo   - Grafana:    http://localhost:3000
echo     Username: admin
echo     Password: admin
echo.
echo Note: Make sure your NAAG services are running with Actuator endpoints enabled.
echo Check Prometheus targets at: http://localhost:9090/targets
