#!/bin/bash
URL="http://localhost:9081/api/messages/count"

echo "Polling queue depth (Ctrl+C to stop)..."
echo ""

while true; do
    TIMESTAMP=$(date '+%H:%M:%S')
    RESULT=$(curl -s "$URL" 2>/dev/null)
    if [ $? -eq 0 ] && [ -n "$RESULT" ]; then
        DEPTH=$(echo "$RESULT" | grep -oP '"depth":\K[0-9]+')
        echo "[$TIMESTAMP] Queue depth: $DEPTH"
    else
        echo "[$TIMESTAMP] Server not reachable"
    fi
    sleep 2
done
