#!/bin/bash
PORT=9081
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/jms-microprofile-app"

echo "Checking if port $PORT is in use..."

PID=$(lsof -t -i:$PORT 2>/dev/null || ss -tlnp "sport = :$PORT" 2>/dev/null | awk 'NR>1{print $NF}' | grep -oP 'pid=\K\d+')

if [ -n "$PID" ]; then
    PROC_NAME=$(ps -p "$PID" -o comm= 2>/dev/null)
    echo "Port $PORT is in use by process '$PROC_NAME' (PID: $PID). Killing it..."
    kill -9 "$PID" 2>/dev/null
    sleep 3
    echo "Process killed."
else
    echo "Port $PORT is free."
fi

echo "Building and starting Liberty server..."
cd "$PROJECT_DIR"
mvn clean package -DskipTests liberty:run
