#!/bin/bash
ADMIN_USER="admin"
ADMIN_PASS="admin123"
BASE_URL="https://localhost:9444/IBMJMXConnectorREST"
MBEAN_URL="$BASE_URL/mbeans/WebSphere%3Afeature%3Dkernel%2Cname%3DServerEndpointControl"
TARGET="jms-microprofile-app#jms-microprofile-app.war#JmsConsumerMDB"
TARGET_PARAM="{\"params\":[{\"value\":\"$TARGET\",\"type\":\"java.lang.String\"}],\"signature\":[\"java.lang.String\"]}"

ACTION="${1:-status}"

case "$ACTION" in
    list)
        echo "Listing all pausable endpoints..."
        curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d '{"params":[],"signature":[]}' \
            "$MBEAN_URL/operations/listEndpoints" | python3 -m json.tool 2>/dev/null || echo "Error: could not reach server"
        ;;
    pause)
        echo "Pausing activation spec via MBean..."
        RESULT=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d "$TARGET_PARAM" \
            "$MBEAN_URL/operations/pause" 2>/dev/null)
        if [ $? -eq 0 ]; then
            echo "Activation spec PAUSED - messages will queue up"
        else
            echo "Error: $RESULT"
        fi
        ;;
    resume)
        echo "Resuming activation spec via MBean..."
        RESULT=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d "$TARGET_PARAM" \
            "$MBEAN_URL/operations/resume" 2>/dev/null)
        if [ $? -eq 0 ]; then
            echo "Activation spec RESUMED - consuming messages"
        else
            echo "Error: $RESULT"
        fi
        ;;
    status)
        echo "Checking activation spec status..."
        RESULT=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d "$TARGET_PARAM" \
            "$MBEAN_URL/operations/isPaused" 2>/dev/null)
        PAUSED=$(echo "$RESULT" | grep -oP '"value"\s*:\s*"\K[^"]+')
        if [ "$PAUSED" = "true" ]; then
            echo "Activation spec status: PAUSED"
        elif [ "$PAUSED" = "false" ]; then
            echo "Activation spec status: RUNNING"
        else
            echo "Error: could not determine status"
        fi
        ;;
    *)
        echo "Usage: $0 {pause|resume|status|list}"
        exit 1
        ;;
esac
