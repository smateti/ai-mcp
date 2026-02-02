#!/bin/bash
# Create the app-logs index with proper mapping for application log data

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX_NAME="app-logs"

echo "=== Creating $INDEX_NAME index ==="

# Delete existing index if present
curl -s -X DELETE "$ES_URL/$INDEX_NAME" 2>/dev/null
echo ""

# Create index with mapping
curl -s -X PUT "$ES_URL/$INDEX_NAME" -H "Content-Type: application/json" -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "log_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "containerName": {
        "type": "keyword",
        "fields": {
          "text": { "type": "text" }
        }
      },
      "namespace": {
        "type": "keyword",
        "fields": {
          "text": { "type": "text" }
        }
      },
      "message": {
        "type": "text",
        "analyzer": "log_analyzer",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 4096
          }
        }
      },
      "timestamp": {
        "type": "date",
        "format": "strict_date_optional_time||epoch_millis"
      },
      "level": {
        "type": "keyword"
      },
      "correlationId": {
        "type": "keyword"
      },
      "stackTrace": {
        "type": "text",
        "index": false
      },
      "serviceName": {
        "type": "keyword"
      },
      "environment": {
        "type": "keyword"
      }
    }
  }
}'

echo ""
echo ""
echo "=== Index $INDEX_NAME created ==="
curl -s "$ES_URL/$INDEX_NAME/_mapping" | python -m json.tool 2>/dev/null || curl -s "$ES_URL/$INDEX_NAME/_mapping"
echo ""
