# Elasticsearch Audit Log Index Setup Script (PowerShell)
# This script creates the index template and index for NAAG audit logs

param(
    [string]$EsHost = "localhost",
    [string]$EsPort = "9200"
)

$EsUrl = "http://${EsHost}:${EsPort}"

Write-Host "Setting up Elasticsearch audit log index at ${EsUrl}..." -ForegroundColor Cyan

# Index template JSON
$indexTemplate = @'
{
  "index_patterns": ["naagi-audit-logs*"],
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
          "format": "date_hour_minute_second_millis||epoch_millis||yyyy-MM-dd'T'HH:mm:ss.SSS"
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
}
'@

try {
    # Create index template
    Write-Host "Creating index template..." -ForegroundColor Yellow
    $response = Invoke-RestMethod -Uri "${EsUrl}/_index_template/naagi-audit-logs-template" `
        -Method PUT `
        -ContentType "application/json" `
        -Body $indexTemplate
    Write-Host "Index template created: $($response | ConvertTo-Json -Compress)" -ForegroundColor Green
}
catch {
    Write-Host "Error creating index template: $_" -ForegroundColor Red
}

try {
    # Create the index
    Write-Host "Creating index naagi-audit-logs..." -ForegroundColor Yellow
    $response = Invoke-RestMethod -Uri "${EsUrl}/naagi-audit-logs" `
        -Method PUT `
        -ContentType "application/json"
    Write-Host "Index created: $($response | ConvertTo-Json -Compress)" -ForegroundColor Green
}
catch {
    if ($_.Exception.Response.StatusCode -eq 400) {
        Write-Host "Index already exists (this is OK)" -ForegroundColor Yellow
    } else {
        Write-Host "Error creating index: $_" -ForegroundColor Red
    }
}

try {
    # Verify index was created
    Write-Host "Verifying index..." -ForegroundColor Yellow
    $response = Invoke-RestMethod -Uri "${EsUrl}/naagi-audit-logs/_mapping" -Method GET
    Write-Host "Index mapping verified" -ForegroundColor Green
}
catch {
    Write-Host "Error verifying index: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "Elasticsearch audit log index setup complete!" -ForegroundColor Cyan
Write-Host "Index: naagi-audit-logs" -ForegroundColor White
Write-Host "URL: ${EsUrl}/naagi-audit-logs" -ForegroundColor White
