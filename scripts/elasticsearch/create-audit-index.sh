#!/bin/bash

# Elasticsearch Audit Log Index Setup Script
# This script creates the index template and index for NAAG audit logs

ES_HOST="${ES_HOST:-localhost}"
ES_PORT="${ES_PORT:-9200}"
ES_URL="http://${ES_HOST}:${ES_PORT}"

echo "Setting up Elasticsearch audit log index at ${ES_URL}..."

# Create index template
echo "Creating index template..."
curl -X PUT "${ES_URL}/_index_template/naag-audit-logs-template" \
  -H "Content-Type: application/json" \
  -d '{
  "index_patterns": ["naag-audit-logs*"],
  "priority": 100,
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "index.mapping.total_fields.limit": 2000,
      "analysis": {
        "analyzer": {
          "lowercase_keyword": {
            "type": "custom",
            "tokenizer": "keyword",
            "filter": ["lowercase"]
          }
        }
      }
    },
    "mappings": {
      "properties": {
        "id": {
          "type": "keyword"
        },
        "userId": {
          "type": "keyword"
        },
        "action": {
          "type": "keyword"
        },
        "entityType": {
          "type": "keyword"
        },
        "entityId": {
          "type": "keyword"
        },
        "details": {
          "type": "text",
          "analyzer": "standard",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "ipAddress": {
          "type": "keyword"
        },
        "userAgent": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 500
            }
          }
        },
        "categoryId": {
          "type": "keyword"
        },
        "timestamp": {
          "type": "date",
          "format": "date_hour_minute_second_millis||epoch_millis||yyyy-MM-dd'\''T'\''HH:mm:ss.SSS"
        },
        "status": {
          "type": "keyword"
        },
        "errorMessage": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 500
            }
          }
        },
        "applicationName": {
          "type": "keyword"
        },
        "environment": {
          "type": "keyword"
        },
        "hostname": {
          "type": "keyword"
        }
      }
    }
  }
}'

echo ""
echo ""

# Create the index
echo "Creating index naag-audit-logs..."
curl -X PUT "${ES_URL}/naag-audit-logs" \
  -H "Content-Type: application/json"

echo ""
echo ""

# Verify index was created
echo "Verifying index..."
curl -s "${ES_URL}/naag-audit-logs/_mapping" | head -100

echo ""
echo "Elasticsearch audit log index setup complete!"
echo ""
echo "Index: naag-audit-logs"
echo "URL: ${ES_URL}/naag-audit-logs"
