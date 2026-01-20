#!/bin/bash

# Start Prometheus and Grafana for NAAG Platform metrics

echo "Starting NAAG Metrics Stack..."
echo "================================"

cd "$(dirname "$0")/.."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker first."
    exit 1
fi

# Start the services
docker-compose up -d

echo ""
echo "Metrics stack started successfully!"
echo ""
echo "Access points:"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana:    http://localhost:3000"
echo "    Username: admin"
echo "    Password: admin"
echo ""
echo "Note: Make sure your NAAG services are running with Actuator endpoints enabled."
echo "Check Prometheus targets at: http://localhost:9090/targets"
