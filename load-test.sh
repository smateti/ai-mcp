#!/bin/bash
BASE_URL="http://localhost:9081/api/messages"
TOTAL=1000
SUCCESS=0
FAIL=0

echo "Sending $TOTAL messages to $BASE_URL ..."
echo ""

START=$(date +%s%N)

for i in $(seq 1 $TOTAL); do
    BODY="Message #$i - Timestamp: $(date '+%Y-%m-%d %H:%M:%S.%3N')"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL" -H "Content-Type: text/plain" -d "$BODY")
    if [ "$HTTP_CODE" -eq 200 ]; then
        SUCCESS=$((SUCCESS + 1))
    else
        FAIL=$((FAIL + 1))
    fi

    if [ $((i % 100)) -eq 0 ]; then
        echo "  Sent $i / $TOTAL messages..."
    fi
done

END=$(date +%s%N)
ELAPSED_MS=$(( (END - START) / 1000000 ))
ELAPSED_SEC=$(awk "BEGIN {printf \"%.3f\", $ELAPSED_MS / 1000}")
RATE=$(awk "BEGIN {printf \"%.1f\", $TOTAL / ($ELAPSED_MS / 1000)}")

echo ""
echo "===== Load Test Complete ====="
echo "  Total:     $TOTAL"
echo "  Success:   $SUCCESS"
echo "  Failed:    $FAIL"
echo "  Duration:  ${ELAPSED_SEC}s"
echo "  Rate:      ${RATE} msg/sec"
echo ""

echo "Checking queue depth..."
RESULT=$(curl -s "$BASE_URL/count" 2>/dev/null)
echo "  $RESULT"
