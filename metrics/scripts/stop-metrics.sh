#!/bin/bash

# Stop Prometheus and Grafana

echo "Stopping NAAG Metrics Stack..."

cd "$(dirname "$0")/.."

docker-compose down

echo "Metrics stack stopped."
