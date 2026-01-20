#!/bin/bash

# Elasticsearch Stop Script (Bash)
# This script stops Elasticsearch Docker containers

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/elasticsearch-docker-compose.yml"
REMOVE_VOLUMES=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --remove-volumes|-v)
            REMOVE_VOLUMES=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "Stopping Elasticsearch..."

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "ERROR: Docker Compose file not found at: $COMPOSE_FILE"
    exit 1
fi

if [ "$REMOVE_VOLUMES" = true ]; then
    echo "Stopping and removing volumes (data will be deleted)..."
    docker-compose -f "$COMPOSE_FILE" down -v
else
    docker-compose -f "$COMPOSE_FILE" down
fi

echo "Elasticsearch stopped successfully"
