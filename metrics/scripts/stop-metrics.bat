@echo off
REM Stop Prometheus and Grafana

echo Stopping NAAG Metrics Stack...

cd /d "%~dp0\.."

docker-compose down

echo Metrics stack stopped.
