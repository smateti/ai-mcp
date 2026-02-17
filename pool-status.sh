#!/bin/bash
ADMIN_USER="admin"
ADMIN_PASS="admin123"
BASE_URL="https://localhost:9444/IBMJMXConnectorREST"
MBEAN_URL="$BASE_URL/mbeans/WebSphere%3AjndiName%3Djms%2FSimpleConnectionFactory%2Cname%3Djms%2FSimpleConnectionFactory%2FconnectionManager%2Ctype%3Dcom.ibm.ws.jca.cm.mbean.ConnectionManagerMBean"

ACTION="${1:-status}"

case "$ACTION" in
    status)
        echo "Connection Pool Status (jms/SimpleConnectionFactory)"
        echo "====================================================="
        RESULT=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d '{"params":[],"signature":[]}' \
            "$MBEAN_URL/operations/showPoolContents" 2>/dev/null)

        echo "$RESULT" | grep -oP '"value"\s*:\s*"\K[^"]+' | sed 's/\\r\\n/\n/g' | while IFS= read -r line; do
            case "$line" in
                maxPoolSize=*) echo "  Max Pool Size:  ${line#*=}" ;;
                size=*)        echo "  Current Size:   ${line#*=}" ;;
                waiting=*)     echo "  Waiting:        ${line#*=}" ;;
                unshared=*)    echo "  In Use (unsh):  ${line#*=}" ;;
                shared=*)      echo "  In Use (shared):${line#*=}" ;;
                available=*)   echo "  Available:      ${line#*=}" ;;
                *ManagedConnection*) echo "  $line" ;;
            esac
        done
        ;;
    purge)
        echo "Purging connection pool..."
        curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d '{"params":[{"value":"normal","type":"java.lang.String"}],"signature":["java.lang.String"]}' \
            "$MBEAN_URL/operations/purgePoolContents" > /dev/null 2>&1
        echo "Connection pool purged."
        ;;
    purge-immediate)
        echo "Purging connection pool immediately..."
        curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d '{"params":[{"value":"immediate","type":"java.lang.String"}],"signature":["java.lang.String"]}' \
            "$MBEAN_URL/operations/purgePoolContents" > /dev/null 2>&1
        echo "Connection pool purged immediately."
        ;;
    watch)
        echo "Polling connection pool (Ctrl+C to stop)..."
        echo ""
        while true; do
            TIMESTAMP=$(date '+%H:%M:%S')
            RESULT=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
                -H "Content-Type: application/json" \
                -d '{"params":[],"signature":[]}' \
                "$MBEAN_URL/operations/showPoolContents" 2>/dev/null)

            SIZE=$(echo "$RESULT" | grep -oP 'size=\K[0-9]+')
            AVAILABLE=$(echo "$RESULT" | grep -oP 'available=\K[0-9]+')
            UNSHARED=$(echo "$RESULT" | grep -oP 'unshared=\K[0-9]+')
            WAITING=$(echo "$RESULT" | grep -oP 'waiting=\K[0-9]+')
            MAX=$(echo "$RESULT" | grep -oP 'maxPoolSize=\K[0-9]+')

            echo "[$TIMESTAMP] size=$SIZE  available=$AVAILABLE  inUse=$UNSHARED  waiting=$WAITING  max=$MAX"
            sleep 2
        done
        ;;
    *)
        echo "Usage: $0 {status|purge|purge-immediate|watch}"
        exit 1
        ;;
esac
