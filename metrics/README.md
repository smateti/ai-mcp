# NAAG Platform Metrics

This folder contains the Prometheus and Grafana setup for monitoring the NAAG Platform microservices.

## Prerequisites

- Docker and Docker Compose installed
- NAAG services running with Actuator endpoints enabled

## Quick Start

### Windows
```batch
scripts\start-metrics.bat
```

### Linux/Mac
```bash
chmod +x scripts/start-metrics.sh
./scripts/start-metrics.sh
```

## Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Prometheus | http://localhost:9090 | N/A |
| Grafana | http://localhost:3000 | admin / admin |

## Monitored Services

| Service | Port | Metrics Endpoint |
|---------|------|------------------|
| naag-chat-app | 8080 | /actuator/prometheus |
| naag-rag-service | 8082 | /actuator/prometheus |
| naag-category-admin | 8084 | /actuator/prometheus |
| naag-tool-registry | 8085 | /actuator/prometheus |
| naag-ai-orchestrator | 8086 | /actuator/prometheus |

## Dashboards

Pre-configured Grafana dashboards are automatically provisioned:

1. **NAAG Platform Overview** - High-level view of all services
2. **NAAG RAG Service** - Detailed RAG metrics (embedding, search, LLM times)
3. **NAAG AI Orchestrator** - Tool selection and orchestration metrics

## Configuration

### Prometheus
Edit `prometheus/prometheus.yml` to modify scrape targets or intervals.

### Grafana
- Datasources: `grafana/provisioning/datasources/datasources.yml`
- Dashboard provisioning: `grafana/provisioning/dashboards/dashboards.yml`
- Dashboard JSON: `grafana/dashboards/`

## Service Configuration

Each NAAG service needs the following in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    prometheus:
      enabled: true
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

## Stopping the Stack

### Windows
```batch
scripts\stop-metrics.bat
```

### Linux/Mac
```bash
./scripts/stop-metrics.sh
```

## Key Metrics

### RAG Service
- `rag_embedding_duration_seconds` - Embedding generation time
- `rag_vector_search_duration_seconds` - Qdrant search time
- `rag_llm_chat_duration_seconds` - LLM response time
- `rag_query_duration_seconds` - Total query time
- `rag_cache_hits` / `rag_cache_misses` - Cache statistics
- `rag_documents_ingested` / `rag_chunks_created` - Ingestion stats

### AI Orchestrator
- `orchestrator_orchestration_duration_seconds` - Total orchestration time
- `orchestrator_tool_selection_duration_seconds` - LLM tool selection time
- `orchestrator_tool_execution_duration_seconds` - MCP tool execution time
- `orchestrator_confidence_high/medium/low` - Confidence distribution

### Chat App
- `chat_message_processing_duration_seconds` - Message processing time
- `chat_orchestrator_call_duration_seconds` - Orchestrator API call time
- `chat_messages_total` / `chat_messages_errors` - Message statistics
- `chat_sessions_created` / `chat_sessions_deleted` - Session stats

### Tool Registry
- `toolregistry_registration_duration_seconds` - Tool registration time
- `toolregistry_tools_registered/deleted/updated` - CRUD operations
- `toolregistry_tools_total` - Total registered tools

### Category Admin
- `categoryadmin_categories_created/updated/deleted` - Category operations
- `categoryadmin_documents_uploaded/ingested` - Document operations
