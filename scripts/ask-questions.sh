#!/bin/bash
# Script to ask sample questions in each category to populate audit trail

CHAT_APP_URL="http://localhost:8087"

# Function to ask a question via streaming API
ask_question() {
    local category_id="$1"
    local category_name="$2"
    local question="$3"
    local session_id="test-session-$(date +%s%N)"

    echo "Asking in $category_name: $question"

    # Use the streaming endpoint and collect the response
    curl -s -X POST "$CHAT_APP_URL/api/chat/stream" \
        -H "Content-Type: application/json" \
        -d "{
            \"sessionId\": \"$session_id\",
            \"message\": \"$question\",
            \"categoryId\": \"$category_id\",
            \"categoryName\": \"$category_name\",
            \"userId\": \"test-user\"
        }" --max-time 60 2>/dev/null | head -c 500

    echo ""
    echo "---"
    sleep 2
}

echo "=== Asking questions in Service Development ==="
ask_question "service-development" "Service Development" "What is dependency injection in Spring?"
ask_question "service-development" "Service Development" "How do I create a REST API endpoint?"
ask_question "service-development" "Service Development" "What is the difference between @Component and @Service?"
ask_question "service-development" "Service Development" "How to handle exceptions in Spring Boot?"
ask_question "service-development" "Service Development" "What is Spring Data JPA?"

echo ""
echo "=== Asking questions in Batch Development ==="
ask_question "batch-development" "Batch Development" "What is Spring Batch?"
ask_question "batch-development" "Batch Development" "How to create a batch job in Spring?"
ask_question "batch-development" "Batch Development" "What is the difference between chunk and tasklet?"
ask_question "batch-development" "Batch Development" "How to schedule a batch job?"

echo ""
echo "=== Asking questions in UI Development ==="
ask_question "ui-development" "UI Development" "What is React hooks?"
ask_question "ui-development" "UI Development" "How to manage state in React?"
ask_question "ui-development" "UI Development" "What is the virtual DOM?"
ask_question "ui-development" "UI Development" "How to make API calls from React?"

echo ""
echo "=== Asking questions in Misc Development ==="
ask_question "misc-development" "Misc Development" "What is Docker?"
ask_question "misc-development" "Misc Development" "How to write a Dockerfile?"
ask_question "misc-development" "Misc Development" "What is CI/CD pipeline?"
ask_question "misc-development" "Misc Development" "How to write unit tests?"

echo ""
echo "=== Asking questions in Data Retrieval ==="
ask_question "data-retrieval" "Data Retrieval" "How to call external APIs?"
ask_question "data-retrieval" "Data Retrieval" "What is REST client in Spring?"
ask_question "data-retrieval" "Data Retrieval" "How to handle API errors?"

echo ""
echo "=== Done! Check http://localhost:8085/user-questions ==="
