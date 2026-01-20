#!/bin/bash

# Elasticsearch Setup Script (Bash)
# This script starts Elasticsearch via Docker Compose and creates the audit log index

set -e

ES_HOST="${ES_HOST:-localhost}"
ES_PORT="${ES_PORT:-9200}"
ES_URL="http://${ES_HOST}:${ES_PORT}"
SKIP_DOCKER=false
SKIP_INDEX=false
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-docker)
            SKIP_DOCKER=true
            shift
            ;;
        --skip-index)
            SKIP_INDEX=true
            shift
            ;;
        --host)
            ES_HOST="$2"
            ES_URL="http://${ES_HOST}:${ES_PORT}"
            shift 2
            ;;
        --port)
            ES_PORT="$2"
            ES_URL="http://${ES_HOST}:${ES_PORT}"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "========================================"
echo "  NAAG Elasticsearch Setup"
echo "========================================"
echo ""

# Step 1: Start Docker Compose
if [ "$SKIP_DOCKER" = false ]; then
    echo "[1/3] Starting Elasticsearch via Docker Compose..."

    COMPOSE_FILE="${SCRIPT_DIR}/elasticsearch-docker-compose.yml"

    if [ ! -f "$COMPOSE_FILE" ]; then
        echo "ERROR: Docker Compose file not found at: $COMPOSE_FILE"
        exit 1
    fi

    docker-compose -f "$COMPOSE_FILE" up -d
    echo "Docker Compose started successfully"
else
    echo "[1/3] Skipping Docker Compose (--skip-docker flag set)"
fi

# Step 2: Wait for Elasticsearch to be ready
echo ""
echo "[2/3] Waiting for Elasticsearch to be ready..."

MAX_ATTEMPTS=30
ATTEMPT=0
READY=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ] && [ "$READY" = false ]; do
    ATTEMPT=$((ATTEMPT + 1))

    HEALTH=$(curl -s "${ES_URL}/_cluster/health" 2>/dev/null || echo "")

    if echo "$HEALTH" | grep -q '"status":"green"\|"status":"yellow"'; then
        READY=true
        STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        echo "Elasticsearch is ready (status: $STATUS)"
    else
        echo "  Attempt $ATTEMPT/$MAX_ATTEMPTS - Elasticsearch not ready yet..."
        sleep 5
    fi
done

if [ "$READY" = false ]; then
    echo "ERROR: Elasticsearch did not become ready in time"
    echo "Check if Docker containers are running: docker ps"
    exit 1
fi

# Step 3: Create the audit log index
if [ "$SKIP_INDEX" = false ]; then
    echo ""
    echo "[3/3] Creating audit log index..."

    INDEX_SCRIPT="${SCRIPT_DIR}/create-audit-index.sh"

    if [ -f "$INDEX_SCRIPT" ]; then
        ES_HOST="$ES_HOST" ES_PORT="$ES_PORT" bash "$INDEX_SCRIPT"
    else
        echo "WARNING: Index creation script not found at: $INDEX_SCRIPT"
        echo "You may need to create the index manually"
    fi
else
    echo "[3/3] Skipping index creation (--skip-index flag set)"
fi

echo ""
echo "========================================"
echo "  Setup Complete!"
echo "========================================"
echo ""
echo "Elasticsearch: ${ES_URL}"
echo "Kibana:        http://${ES_HOST}:5601"
echo ""
echo "Next steps:"
echo "1. Enable Elasticsearch in application.yml:"
echo "   naag.elasticsearch.enabled: true"
echo ""
echo "2. Restart naag-category-admin"
echo ""
echo "3. Trigger initial sync:"
echo "   curl -X POST http://localhost:8085/api/elasticsearch/sync/full"
echo ""
