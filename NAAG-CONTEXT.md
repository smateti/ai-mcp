# NAAG Platform - Context Document

> **Last Updated**: January 17, 2026
> **Purpose**: Comprehensive reference for understanding the NAAG platform architecture, services, and design decisions.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Services](#services)
4. [Data Flow](#data-flow)
5. [Technology Stack](#technology-stack)
6. [Configuration](#configuration)
7. [Design Documents](#design-documents)
8. [Backup/Legacy Services](#backuplegacy-services)
9. [Development Guidelines](#development-guidelines)
10. [Quick Reference](#quick-reference)

---

## Overview

**NAAG (Nimbus AI Agent)** is a microservices-based platform for intelligent task automation using:

- **AI/LLM** - Natural language understanding and response generation
- **RAG** - Retrieval-Augmented Generation for document-based Q&A
- **MCP** - Model Context Protocol for tool execution

The platform enables users to interact via natural language chat, with the system intelligently routing requests to appropriate tools or document-based knowledge retrieval.

---

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      User Interface Layer                        │
│  ┌────────────────────────┐    ┌─────────────────────────────┐  │
│  │   naag-chat-app        │    │   naag-category-admin       │  │
│  │   (Port 8087)          │    │   (Port 8085)               │  │
│  │   User Chat Interface  │    │   Admin Dashboard           │  │
│  └───────────┬────────────┘    └─────────────┬───────────────┘  │
└──────────────┼───────────────────────────────┼──────────────────┘
               │                               │
               │ POST /api/orchestrate         │ CRUD operations
               ▼                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Intelligence Layer                           │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              naag-ai-orchestrator (Port 8086)              │ │
│  │  • Intent Detection (tool vs RAG vs conversation)         │ │
│  │  • LLM-based Tool Selection                               │ │
│  │  • Parameter Extraction                                    │ │
│  │  • Response Generation                                     │ │
│  └──────────────────┬─────────────────┬───────────────────────┘ │
└─────────────────────┼─────────────────┼─────────────────────────┘
                      │                 │
        ┌─────────────┘                 └─────────────┐
        ▼                                             ▼
┌───────────────────────────┐         ┌───────────────────────────┐
│    Tool Execution Layer   │         │     Knowledge Layer       │
│ ┌───────────────────────┐ │         │ ┌───────────────────────┐ │
│ │ naag-mcp-gateway      │ │         │ │ naag-rag-service      │ │
│ │ (Port 8082)           │ │         │ │ (Port 8080)           │ │
│ │ • JSON-RPC 2.0        │ │         │ │ • Document Ingestion  │ │
│ │ • Tool Routing        │ │         │ │ • Embeddings          │ │
│ │ • SSE Streaming       │ │         │ │ • Vector Search       │ │
│ └───────────┬───────────┘ │         │ │ • Q&A Generation      │ │
└─────────────┼─────────────┘         │ └───────────────────────┘ │
              │                        └───────────────────────────┘
              ▼                                     │
┌───────────────────────────┐                      ▼
│   Tool Services Layer     │         ┌───────────────────────────┐
│ ┌───────────────────────┐ │         │   External Services       │
│ │ naag-utility-tools    │ │         │ ┌───────────────────────┐ │
│ │ (Port 8083)           │ │         │ │ Qdrant (Port 6333)    │ │
│ │ • Echo, Calculator    │ │         │ │ Vector Database       │ │
│ │ • Time utilities      │ │         │ └───────────────────────┘ │
│ └───────────────────────┘ │         │ ┌───────────────────────┐ │
│ ┌───────────────────────┐ │         │ │ LLM Server            │ │
│ │ naag-tool-registry    │ │         │ │ llama.cpp (8000)      │ │
│ │ (Port 8081)           │ │         │ │ Ollama (11434)        │ │
│ │ • Tool Definitions    │ │         │ └───────────────────────┘ │
│ │ • OpenAPI Parsing     │ │         └───────────────────────────┘
│ └───────────────────────┘ │
└───────────────────────────┘
```

### Design Principles

1. **Services Only - No Shared Libraries**: Each service is self-contained with its own dependencies
2. **HTTP REST Communication**: Services communicate via REST APIs only
3. **Naming Convention**: `naag-*` prefix with descriptive 3-word names
4. **Independent Deployment**: Each service can be updated/scaled independently

---

## Services

### 1. naag-rag-service (Port 8080)

**Purpose**: RAG operations, document storage, and embeddings

**Responsibilities**:
- Document ingestion and parsing (PDF, DOCX, TXT)
- Text chunking (max 1200 chars, overlap 200 chars)
- Vector embeddings generation
- Qdrant vector database integration
- RAG query operations with LLM
- Caching layer (TTL: 60s default, 300s embeddings)

**Key Endpoints**:
- `POST /api/documents/upload` - Upload documents
- `POST /api/rag/query` - RAG query
- `GET /api/documents` - List documents

**Dependencies**:
- PDFBox 2.0.31 (PDF parsing)
- Apache POI 5.2.5 (DOCX parsing)
- EhCache 3.10.8 (caching)
- H2 Database (file: `./data/naag-rag-db`)

---

### 2. naag-tool-registry (Port 8081)

**Purpose**: Register and manage tool definitions

**Responsibilities**:
- CRUD operations for tool definitions
- OpenAPI specification parsing and registration
- Tool metadata storage (schema, endpoint, method)

**Key Endpoints**:
- `GET /api/tools` - List all tools
- `POST /api/tools` - Register tool
- `POST /api/tools/openapi` - Register from OpenAPI spec
- `DELETE /api/tools/{id}` - Remove tool

**Dependencies**:
- Swagger Parser 2.1.19 (OpenAPI parsing)
- Thymeleaf (admin UI)
- H2 Database (file: `./data/naag-tool-registry`)

---

### 3. naag-mcp-gateway (Port 8082)

**Purpose**: MCP Protocol gateway for tool execution

**Responsibilities**:
- JSON-RPC 2.0 protocol implementation
- Tool execution coordination
- Registry loading and dynamic tool discovery
- HTTP calls to registered tool endpoints
- Server-Sent Events (SSE) streaming

**Key Endpoints**:
- `POST /mcp/execute` - Execute tool
- `GET /mcp/tools` - List available tools
- `GET /mcp/stream` - SSE streaming endpoint

**Flow**:
1. Loads tool definitions from naag-tool-registry (8081)
2. Receives tool execution requests
3. Routes to naag-utility-tools (8083) for built-in tools
4. Routes to external registered endpoints for other tools

---

### 4. naag-utility-tools (Port 8083)

**Purpose**: Provide basic utility tools as independent service

**Available Tools**:
- `POST /api/echo` - Echo messages
- `POST /api/add` - Add numbers
- `POST /api/subtract` - Subtract numbers
- `GET /api/time` - Current time/date

**Startup Behavior**: Auto-registers itself with naag-tool-registry on startup

---

### 5. naag-category-admin (Port 8085)

**Purpose**: Admin UI for category and document management

**Responsibilities**:
- Category CRUD operations
- Category-to-tool assignments
- Document upload and management
- Q&A preview for documents
- Tool registration interface
- Tool health monitoring

**Key Features**:
- Enhanced document upload with Q&A generation preview
- Document chunking statistics
- Category-based filtering
- Tool health status (UP/DOWN)

**Delegates To**:
- naag-rag-service for document parsing
- naag-tool-registry for tool management

---

### 6. naag-ai-orchestrator (Port 8086)

**Purpose**: Central intelligence layer for LLM-powered routing

**Responsibilities**:
- Intent detection (tool call vs RAG query vs conversation)
- LLM-based tool selection
- Parameter extraction from natural language
- Response generation
- Category-based tool filtering
- Confidence-based routing

**Key Endpoints**:
- `POST /api/orchestrate` - Main orchestration endpoint
- `POST /api/chat` - Chat endpoint

**Configuration**:
- LLM Provider: llamacpp-openai (configurable)
- Base URL: localhost:8000
- Model: llama3.1
- Tool selection thresholds: high 0.8, low 0.5

**Service Dependencies**:
- naag-rag-service (8080) - Direct RAG queries
- naag-tool-registry (8081) - Tool schemas
- naag-mcp-gateway (8082) - Tool execution
- naag-category-admin (8085) - Category configuration

---

### 7. naag-chat-app (Port 8087)

**Purpose**: User chat interface for NAAG platform

**Responsibilities**:
- Web UI for chat interaction
- Session management
- Chat history tracking
- Request forwarding to orchestrator
- Response display

**Key Endpoints**:
- `GET /` - Chat UI
- `POST /api/chat` - Send message
- `GET /api/history` - Get chat history

---

## Data Flow

### Chat Message Processing Flow

```
User Input (naag-chat-app:8087)
        │
        ▼
POST /api/orchestrate (naag-ai-orchestrator:8086)
        │
        ├──► Intent Detection (LLM)
        │
        ├──► If TOOL_CALL:
        │    │
        │    ├──► Get tool schema (naag-tool-registry:8081)
        │    ├──► Extract parameters (LLM)
        │    ├──► Execute tool (naag-mcp-gateway:8082)
        │    │         │
        │    │         └──► Route to tool endpoint
        │    │              (naag-utility-tools:8083 or external)
        │    │
        │    └──► Generate response (LLM)
        │
        ├──► If RAG_QUERY:
        │    │
        │    └──► Query documents (naag-rag-service:8080)
        │              │
        │              ├──► Vector search (Qdrant:6333)
        │              └──► Generate answer (LLM)
        │
        └──► If CONVERSATION:
             │
             └──► Direct LLM response
```

### Document Upload Flow

```
Admin Upload (naag-category-admin:8085)
        │
        ▼
POST /api/documents/upload (naag-rag-service:8080)
        │
        ├──► Parse document (PDF/DOCX/TXT)
        ├──► Chunk text (1200 chars, 200 overlap)
        ├──► Generate embeddings (LLM)
        ├──► Store in Qdrant (vector DB)
        └──► Store metadata in H2 DB
```

---

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Spring Boot | 3.3.2 |
| Java | OpenJDK | 21 |
| Build Tool | Maven | 3.x |
| Database | H2 (file-based) | - |
| Vector DB | Qdrant | latest |
| Cache | EhCache | 3.10.8 |
| Monitoring | Micrometer Prometheus | - |
| PDF Parsing | PDFBox | 2.0.31 |
| DOCX Parsing | Apache POI | 5.2.5 |
| OpenAPI Parsing | Swagger Parser | 2.1.19 |
| UI Templates | Thymeleaf | - |
| LLM | llama.cpp / Ollama | - |

---

## Configuration

### LLM Provider Configuration

Located in each service's `application.yml`:

```yaml
llm:
  provider: llamacpp-openai  # Options: ollama-native, ollama-openai, llamacpp-openai
  base-url: http://localhost:8000
  model: llama3.1
  embedding-model: nomic-embed-text
```

### Service Ports

| Service | Port | Database Path |
|---------|------|---------------|
| naag-rag-service | 8080 | ./data/naag-rag-db |
| naag-tool-registry | 8081 | ./data/naag-tool-registry |
| naag-mcp-gateway | 8082 | - |
| naag-utility-tools | 8083 | - |
| naag-category-admin | 8085 | - |
| naag-ai-orchestrator | 8086 | - |
| naag-chat-app | 8087 | - |
| Qdrant | 6333 | - |
| LLM Server | 8000/11434 | - |

### Chunking Configuration (RAG)

```yaml
chunking:
  max-chunk-size: 1200
  overlap: 200
```

### Cache Configuration

```yaml
cache:
  default-ttl: 60
  embeddings-ttl: 300
```

---

## Design Documents

| Document | Location | Description |
|----------|----------|-------------|
| NAAG-FEATURES-DESIGN.md | d:\apps\ws\ws8\ | Platform architecture, flows, audit logging |
| REFACTORING-DESIGN.md | d:\apps\ws\ws8\ | Refactoring principles, service specs, phases |
| NAAG-ADMIN-MANUAL.md | d:\apps\ws\ws8\ | Admin guide for categories/tools/documents |
| NAAG-USER-MANUAL.md | d:\apps\ws\ws8\ | User chat interface guide |

---

## Backup/Legacy Services

The `bkp/` folder contains predecessor versions from the experimental phase:

| Legacy Service | Current Service | Notes |
|----------------|-----------------|-------|
| full-rag-springboot-sync | naag-rag-service | Spring Boot 3.3.2 → Java 21 |
| mcp-spring-boot-server | naag-mcp-gateway | Renamed, upgraded |
| mcp-chat-app | naag-chat-app | Renamed, upgraded |
| dynamic-tool-registry | naag-tool-registry | Renamed, upgraded |
| mcp-category-admin | naag-category-admin | Renamed, upgraded |
| mcp-user-chat | naag-chat-app | Merged functionality |
| service-dependency-api | (removed) | No longer needed |

**Key Changes from Legacy to Current**:
- Spring Boot: 3.2.1 → 3.3.2
- Java: 17 → 21
- Naming: `mcp-*` → `naag-*`
- Architecture: Experimental → Production-ready

---

## Development Guidelines

### Service Communication

```java
// Example: Calling another service
@Value("${services.rag-service.url}")
private String ragServiceUrl;

public RagResponse queryRag(String query) {
    return restTemplate.postForObject(
        ragServiceUrl + "/api/rag/query",
        new RagRequest(query),
        RagResponse.class
    );
}
```

### Adding a New Tool

1. Create endpoint in naag-utility-tools (or external service)
2. Register via naag-category-admin UI or API:
   ```json
   POST /api/tools
   {
     "name": "my-tool",
     "description": "Tool description for LLM",
     "endpoint": "http://localhost:8083/api/my-tool",
     "method": "POST",
     "schema": { ... }
   }
   ```
3. Assign to category for context-based routing

### Startup Order

```bash
# 1. External services
qdrant  # Port 6333
llama-server  # Port 8000

# 2. NAAG services (dependency order)
naag-rag-service        # 8080 - No dependencies
naag-tool-registry      # 8081 - No dependencies
naag-mcp-gateway        # 8082 - Depends on tool-registry
naag-utility-tools      # 8083 - Depends on tool-registry
naag-category-admin     # 8085 - Depends on rag-service, tool-registry
naag-ai-orchestrator    # 8086 - Depends on all core services
naag-chat-app           # 8087 - Depends on orchestrator
```

### Startup Scripts

| Script | Purpose |
|--------|---------|
| start-naag.ps1 | PowerShell - Start all services |
| start-naag.bat | Batch wrapper |
| status-naag.bat | Check service status |
| stop-naag.bat | Stop all services |

### Logging

All services log to: `d:\apps\ws\ws8\logs\`

---

## Quick Reference

### Service URLs (Development)

```
RAG Service:        http://localhost:8080
Tool Registry:      http://localhost:8081
MCP Gateway:        http://localhost:8082
Utility Tools:      http://localhost:8083
Category Admin:     http://localhost:8085
AI Orchestrator:    http://localhost:8086
Chat App:           http://localhost:8087
Qdrant Dashboard:   http://localhost:6333/dashboard
```

### Common API Calls

```bash
# Send chat message
curl -X POST http://localhost:8087/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What time is it?"}'

# Upload document
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@document.pdf" \
  -F "category=general"

# List tools
curl http://localhost:8081/api/tools

# Execute tool directly
curl -X POST http://localhost:8082/mcp/execute \
  -H "Content-Type: application/json" \
  -d '{"tool": "time", "params": {}}'
```

### Health Checks

```bash
# Check all services
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8086/actuator/health
curl http://localhost:8087/actuator/health
```

---

## Junk Folder Reference

The `junk/` folder contains historical design documents and completion reports:

- RAG-MCP-INTEGRATION-DESIGN.md - Initial integration design
- LLM-TOOL-SELECTION-COMPLETE.md - Tool selection implementation
- LLM-STREAMING-COMPLETE.md - Streaming implementation
- LLAMACPP-MIGRATION-COMPLETE.md - LLM migration notes
- LLM-PROVIDER-CONFIGURATION.md - Provider configuration guide
- CATEGORY-BASED-RAG.md - Category-based RAG design
- Various test data and utility scripts

---

*This document serves as the single source of truth for understanding the NAAG platform. Keep it updated as the architecture evolves.*
