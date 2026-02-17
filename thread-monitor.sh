#!/bin/bash
ADMIN_USER="admin"
ADMIN_PASS="admin123"
BASE_URL="https://localhost:9444/IBMJMXConnectorREST"
THREADING_URL="$BASE_URL/mbeans/java.lang%3Atype%3DThreading"
APP_URL="http://localhost:9081/api/threads"

ACTION="${1:-status}"

case "$ACTION" in
    status)
        echo "Thread Pool Status"
        echo "=================="
        RESULT=$(curl -s "$APP_URL" 2>/dev/null)
        if [ -z "$RESULT" ]; then
            echo "  App endpoint not available, falling back to MBeans..."
            THREAD_COUNT=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" "$THREADING_URL/attributes/ThreadCount" 2>/dev/null)
            PEAK=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" "$THREADING_URL/attributes/PeakThreadCount" 2>/dev/null)
            DAEMON=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" "$THREADING_URL/attributes/DaemonThreadCount" 2>/dev/null)
            TOTAL_STARTED=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" "$THREADING_URL/attributes/TotalStartedThreadCount" 2>/dev/null)

            TC=$(echo "$THREAD_COUNT" | grep -oP '"value"\s*:\s*\K[0-9]+')
            PK=$(echo "$PEAK" | grep -oP '"value"\s*:\s*\K[0-9]+')
            DM=$(echo "$DAEMON" | grep -oP '"value"\s*:\s*\K[0-9]+')
            TS=$(echo "$TOTAL_STARTED" | grep -oP '"value"\s*:\s*\K[0-9]+')

            echo "  Current threads:     $TC"
            echo "  Peak threads:        $PK"
            echo "  Daemon threads:      $DM"
            echo "  Total ever started:  $TS"
        else
            TOTAL=$(echo "$RESULT" | grep -oP '"totalThreads"\s*:\s*\K[0-9]+')
            PEAK=$(echo "$RESULT" | grep -oP '"peakThreads"\s*:\s*\K[0-9]+')
            STARTED=$(echo "$RESULT" | grep -oP '"totalStarted"\s*:\s*\K[0-9]+')
            RUNNABLE=$(echo "$RESULT" | grep -oP '"runnable"\s*:\s*\K[0-9]+')
            WAITING=$(echo "$RESULT" | grep -oP '"waiting"\s*:\s*\K[0-9]+')
            TIMED=$(echo "$RESULT" | grep -oP '"timedWaiting"\s*:\s*\K[0-9]+')
            BLOCKED=$(echo "$RESULT" | grep -oP '"blocked"\s*:\s*\K[0-9]+')

            echo "  Total threads:       $TOTAL"
            echo "  Peak threads:        $PEAK"
            echo "  Total started:       $STARTED"
            echo "  ----"
            echo "  Runnable:            $RUNNABLE"
            echo "  Waiting:             $WAITING"
            echo "  Timed Waiting:       $TIMED"
            echo "  Blocked:             $BLOCKED"
        fi
        ;;
    watch)
        echo "Monitoring threads (Ctrl+C to stop)..."
        echo ""
        printf "%-10s  %7s  %7s  %7s  %7s  %7s  %7s\n" "TIME" "TOTAL" "RUNNBL" "WAIT" "TIMED" "BLOCK" "PEAK"
        printf "%-10s  %7s  %7s  %7s  %7s  %7s  %7s\n" "--------" "-----" "------" "----" "-----" "-----" "----"
        while true; do
            TIMESTAMP=$(date '+%H:%M:%S')
            RESULT=$(curl -s "$APP_URL" 2>/dev/null)
            if [ -n "$RESULT" ]; then
                TOTAL=$(echo "$RESULT" | grep -oP '"totalThreads"\s*:\s*\K[0-9]+')
                RUNNABLE=$(echo "$RESULT" | grep -oP '"runnable"\s*:\s*\K[0-9]+')
                WAITING=$(echo "$RESULT" | grep -oP '"waiting"\s*:\s*\K[0-9]+')
                TIMED=$(echo "$RESULT" | grep -oP '"timedWaiting"\s*:\s*\K[0-9]+')
                BLOCKED=$(echo "$RESULT" | grep -oP '"blocked"\s*:\s*\K[0-9]+')
                PEAK=$(echo "$RESULT" | grep -oP '"peakThreads"\s*:\s*\K[0-9]+')
                printf "%-10s  %7s  %7s  %7s  %7s  %7s  %7s\n" "$TIMESTAMP" "$TOTAL" "$RUNNABLE" "$WAITING" "$TIMED" "$BLOCKED" "$PEAK"
            else
                echo "[$TIMESTAMP] Server not reachable"
            fi
            sleep 2
        done
        ;;
    blocked)
        echo "Blocked & Waiting Threads"
        echo "========================="
        RESULT=$(curl -s "$APP_URL/blocked" 2>/dev/null)
        if [ -z "$RESULT" ]; then
            echo "Error: App endpoint not available"
            exit 1
        fi
        echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT"
        ;;
    deadlocks)
        echo "Deadlock Detection"
        echo "=================="
        RESULT=$(curl -s "$APP_URL/deadlocks" 2>/dev/null)
        if [ -z "$RESULT" ]; then
            echo "Error: App endpoint not available"
            exit 1
        fi
        echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT"
        ;;
    long-waiting)
        echo "Long Waiting Threads (stuck / I/O bound)"
        echo "========================================="
        RESULT=$(curl -s "$APP_URL/long-waiting" 2>/dev/null)
        if [ -z "$RESULT" ]; then
            echo "Error: App endpoint not available"
            exit 1
        fi
        echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT"
        ;;
    dump)
        echo "Thread dump..."
        THREADS=$(curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d '{"params":[],"signature":[]}' \
            "$THREADING_URL/operations/dumpAllThreads%28boolean%2Cboolean%29" 2>/dev/null)
        if [ $? -ne 0 ] || [ -z "$THREADS" ]; then
            echo "Triggering server dump via JavaDiagnostics..."
            curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
                -H "Content-Type: application/json" \
                -d '{"params":[{"value":"thread","type":"java.lang.String"}],"signature":["java.lang.String"]}' \
                "$BASE_URL/mbeans/WebSphere%3Atype%3DJavaDiagnostics/operations/dumpJava" 2>/dev/null
            echo "Check server logs for thread dump output."
        else
            echo "$THREADS" | python3 -m json.tool 2>/dev/null || echo "$THREADS"
        fi
        ;;
    reset-peak)
        echo "Resetting peak thread count..."
        curl -s -k -u "$ADMIN_USER:$ADMIN_PASS" -X POST \
            -H "Content-Type: application/json" \
            -d '{"params":[],"signature":[]}' \
            "$THREADING_URL/operations/resetPeakThreadCount" > /dev/null 2>&1
        echo "Peak thread count reset."
        ;;
    *)
        echo "Usage: $0 {status|watch|blocked|deadlocks|long-waiting|dump|reset-peak}"
        exit 1
        ;;
esac
