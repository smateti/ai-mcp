#!/bin/bash
# Test script for RAG REST API
# This script tests all endpoints of the RAG REST API

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "RAG REST API Test Suite"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Health Check
echo -e "${YELLOW}Test 1: Health Check${NC}"
echo "GET ${BASE_URL}/api/rag/health"
curl -s -X GET "${BASE_URL}/api/rag/health" | jq .
echo ""
echo ""

# Test 2: Check OpenAPI spec is accessible
echo -e "${YELLOW}Test 2: OpenAPI Specification${NC}"
echo "GET ${BASE_URL}/openapi.yaml"
curl -s -X GET "${BASE_URL}/openapi.yaml" | head -n 10
echo "..."
echo ""
echo ""

# Test 3: Ingest a test document
echo -e "${YELLOW}Test 3: Ingest Document${NC}"
echo "POST ${BASE_URL}/api/rag/ingest"
INGEST_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/rag/ingest" \
  -H "Content-Type: application/json" \
  -d '{
    "docId": "test-doc-capitals",
    "text": "Paris is the capital of France. It is famous for the Eiffel Tower and the Louvre Museum. France is located in Western Europe. London is the capital of the United Kingdom. The UK is known for Big Ben and Buckingham Palace. Berlin is the capital of Germany, known for the Brandenburg Gate."
  }')

echo "$INGEST_RESPONSE" | jq .

# Check if ingestion was successful
SUCCESS=$(echo "$INGEST_RESPONSE" | jq -r '.success')
if [ "$SUCCESS" = "true" ]; then
    echo -e "${GREEN}✓ Ingestion successful${NC}"
else
    echo -e "${RED}✗ Ingestion failed${NC}"
fi
echo ""
echo ""

# Wait a moment for indexing
echo "Waiting 2 seconds for indexing..."
sleep 2

# Test 4: Query the ingested document
echo -e "${YELLOW}Test 4: Query Document (What is the capital of France?)${NC}"
echo "POST ${BASE_URL}/api/rag/query"
QUERY_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/rag/query" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the capital of France?",
    "topK": 3
  }')

echo "$QUERY_RESPONSE" | jq .

# Check if query was successful
SUCCESS=$(echo "$QUERY_RESPONSE" | jq -r '.success')
if [ "$SUCCESS" = "true" ]; then
    echo -e "${GREEN}✓ Query successful${NC}"
    ANSWER=$(echo "$QUERY_RESPONSE" | jq -r '.answer')
    echo -e "${GREEN}Answer: ${ANSWER}${NC}"
else
    echo -e "${RED}✗ Query failed${NC}"
fi
echo ""
echo ""

# Test 5: Query with different question
echo -e "${YELLOW}Test 5: Query Document (What is Berlin known for?)${NC}"
echo "POST ${BASE_URL}/api/rag/query"
QUERY_RESPONSE_2=$(curl -s -X POST "${BASE_URL}/api/rag/query" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is Berlin known for?",
    "topK": 5
  }')

echo "$QUERY_RESPONSE_2" | jq .

# Check if query was successful
SUCCESS=$(echo "$QUERY_RESPONSE_2" | jq -r '.success')
if [ "$SUCCESS" = "true" ]; then
    echo -e "${GREEN}✓ Query successful${NC}"
    ANSWER=$(echo "$QUERY_RESPONSE_2" | jq -r '.answer')
    echo -e "${GREEN}Answer: ${ANSWER}${NC}"
else
    echo -e "${RED}✗ Query failed${NC}"
fi
echo ""
echo ""

# Test 6: Test validation - empty question
echo -e "${YELLOW}Test 6: Validation Test (empty question)${NC}"
echo "POST ${BASE_URL}/api/rag/query"
VALIDATION_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/rag/query" \
  -H "Content-Type: application/json" \
  -d '{
    "question": ""
  }')

echo "$VALIDATION_RESPONSE" | jq .

# Check if validation error was returned
SUCCESS=$(echo "$VALIDATION_RESPONSE" | jq -r '.success')
if [ "$SUCCESS" = "false" ]; then
    echo -e "${GREEN}✓ Validation working correctly${NC}"
else
    echo -e "${RED}✗ Validation not working${NC}"
fi
echo ""
echo ""

# Test 7: Test validation - invalid topK
echo -e "${YELLOW}Test 7: Validation Test (invalid topK)${NC}"
echo "POST ${BASE_URL}/api/rag/query"
VALIDATION_RESPONSE_2=$(curl -s -X POST "${BASE_URL}/api/rag/query" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "test question",
    "topK": 100
  }')

echo "$VALIDATION_RESPONSE_2" | jq .

# Check if validation error was returned
SUCCESS=$(echo "$VALIDATION_RESPONSE_2" | jq -r '.success')
if [ "$SUCCESS" = "false" ]; then
    echo -e "${GREEN}✓ Validation working correctly${NC}"
else
    echo -e "${RED}✗ Validation not working${NC}"
fi
echo ""
echo ""

echo "=========================================="
echo "Test Suite Complete"
echo "=========================================="
