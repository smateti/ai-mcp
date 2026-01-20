# Elasticsearch Audit Trail Setup

This directory contains scripts and configuration for setting up Elasticsearch to store NAAG audit trail data.

## Directory Contents

```
scripts/elasticsearch/
├── elasticsearch-docker-compose.yml  # Docker Compose for ES + Kibana
├── setup-elasticsearch.ps1           # Full setup script (PowerShell)
├── setup-elasticsearch.sh            # Full setup script (Bash)
├── stop-elasticsearch.ps1            # Stop script (PowerShell)
├── stop-elasticsearch.sh             # Stop script (Bash)
├── create-audit-index.ps1            # Index creation (PowerShell)
├── create-audit-index.sh             # Index creation (Bash)
└── README.md                         # This file
```

## Prerequisites

1. Docker and Docker Compose installed
2. At least 4GB RAM available for Elasticsearch

## Quick Start (One Command)

**PowerShell:**
```powershell
cd d:\apps\ws\ws8\scripts\elasticsearch
.\setup-elasticsearch.ps1
```

**Bash:**
```bash
cd d:/apps/ws/ws8/scripts/elasticsearch
./setup-elasticsearch.sh
```

This will:
1. Start Elasticsearch and Kibana via Docker Compose
2. Wait for Elasticsearch to be ready
3. Create the audit log index with proper mappings

## Manual Setup

### 1. Start Elasticsearch

```bash
cd d:\apps\ws\ws8\scripts\elasticsearch
docker-compose -f elasticsearch-docker-compose.yml up -d
```

Wait for Elasticsearch to be healthy:
```bash
curl http://localhost:9200/_cluster/health
```

### 2. Create the Audit Log Index

**PowerShell:**
```powershell
.\create-audit-index.ps1
```

**Bash:**
```bash
./create-audit-index.sh
```

### 3. Enable Elasticsearch in the Application

Edit `naag-category-admin/src/main/resources/application.yml`:

```yaml
naag:
  elasticsearch:
    enabled: true  # Change from false to true
    environment: development  # or staging, production
```

### 4. Restart the Application

```bash
cd d:\apps\ws\ws8\naag-category-admin
mvn spring-boot:run
```

## Initial Data Migration

After enabling Elasticsearch, you can sync existing audit logs from H2 to Elasticsearch:

**Trigger Full Sync:**
```bash
curl -X POST http://localhost:8085/api/elasticsearch/sync/full
```

**Check Sync Status:**
```bash
curl http://localhost:8085/api/elasticsearch/sync/status
```

## API Endpoints

When Elasticsearch is enabled, the following endpoints are available:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/elasticsearch/sync/status` | GET | Get sync status |
| `/api/elasticsearch/sync/full` | POST | Trigger full sync |
| `/api/elasticsearch/sync/incremental` | POST | Trigger incremental sync |
| `/api/elasticsearch/sync/range?start=&end=` | POST | Sync specific time range |
| `/api/elasticsearch/audit-logs/search` | GET | Search audit logs |
| `/api/elasticsearch/audit-logs/search/text?query=` | GET | Full-text search |
| `/api/elasticsearch/audit-logs/user/{userId}` | GET | Get logs by user |
| `/api/elasticsearch/audit-logs/action/{action}` | GET | Get logs by action |
| `/api/elasticsearch/audit-logs/failures` | GET | Get failure logs |
| `/api/elasticsearch/audit-logs/failures/count` | GET | Count failures |

## Search Examples

**Search by user:**
```bash
curl "http://localhost:8085/api/elasticsearch/audit-logs/user/admin?page=0&size=20"
```

**Full-text search:**
```bash
curl "http://localhost:8085/api/elasticsearch/audit-logs/search/text?query=document%20upload"
```

**Search with filters:**
```bash
curl "http://localhost:8085/api/elasticsearch/audit-logs/search?action=DOCUMENT_UPLOAD&categoryId=cat-123"
```

**Get failures in last 24 hours:**
```bash
curl "http://localhost:8085/api/elasticsearch/audit-logs/failures?hours=24"
```

## Kibana Dashboard

Access Kibana at http://localhost:5601 to:

1. View the `naag-audit-logs` index
2. Create visualizations and dashboards
3. Set up alerts for failures

### Suggested Kibana Visualizations

1. **Audit Actions Over Time** - Line chart of action counts
2. **User Activity** - Pie chart of actions by user
3. **Failure Rate** - Gauge showing failure percentage
4. **Recent Failures** - Data table of recent failures

## Configuration Options

### application.yml

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200  # Elasticsearch URL

naag:
  elasticsearch:
    enabled: true                 # Enable/disable Elasticsearch sync
    environment: development      # Environment tag for logs
    sync:
      interval: 300000            # Incremental sync interval (ms), default 5 minutes
```

## Troubleshooting

### Elasticsearch not connecting

Check if Elasticsearch is running:
```bash
curl http://localhost:9200
```

### Index not created

Run the index creation script:
```powershell
.\create-audit-index.ps1
```

### Sync not working

Check sync status:
```bash
curl http://localhost:8085/api/elasticsearch/sync/status
```

Check application logs for errors.

## Stopping Elasticsearch

**PowerShell:**
```powershell
.\stop-elasticsearch.ps1
```

**Bash:**
```bash
./stop-elasticsearch.sh
```

**To also remove data volumes:**
```powershell
.\stop-elasticsearch.ps1 -RemoveVolumes
```

```bash
./stop-elasticsearch.sh --remove-volumes
```

## Production Considerations

1. **Security**: Enable Elasticsearch security in production
2. **Replicas**: Increase `number_of_replicas` for high availability
3. **Shards**: Adjust `number_of_shards` based on data volume
4. **Index Lifecycle Management (ILM)**: Set up automatic index rotation
5. **Backup**: Configure Elasticsearch snapshots

## Index Lifecycle Management (Optional)

For production, set up ILM to manage index size:

```json
PUT _ilm/policy/naag-audit-policy
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_age": "30d",
            "max_size": "10gb"
          }
        }
      },
      "delete": {
        "min_age": "90d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```
