# NAAG Platform Refactoring Design

**NAAG = Nimbus AI Agent**

## Design Principle

**Services Only - No Shared Libraries**

Each service is self-contained with its own dependencies. Services communicate via HTTP REST APIs only. Code duplication is acceptable for service independence.

---

## Naming Convention

All services use the **naagi-** prefix with 3-word names:
- `naagi-rag-service` (not `rag-service`)
- `naagi-tool-registry` (not `tool-registry`)
- `naagi-mcp-gateway` (not `mcp-gateway`)

---

## Target Services (Ports Ordered by Dependency)

| Port | Service | Dependencies | Purpose |
|------|---------|--------------|---------|
| 8080 | **naagi-rag-service** | None | RAG (ingest, query, embeddings, document parsing) |
| 8081 | **naagi-tool-registry** | None | Register and manage tool definitions |
| 8082 | **naagi-mcp-gateway** | 8081 | MCP protocol gateway for tool execution |
| 8083 | **naagi-utility-tools** | 8081 | Basic utility tools (echo, add, time) |
| 8085 | **naagi-category-admin** | 8080, 8081 | Admin UI for categories, tools, documents |
| 8086 | **naagi-ai-orchestrator** | 8080, 8081, 8082, 8085 | LLM tool selection & intelligent routing |
| 8087 | **naagi-chat-app** | 8086 | User chat interface |

Note: Port 8084 is reserved for test services (not part of core platform).

---

## Target Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           USER INTERFACES                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐              ┌──────────────────┐                     │
│  │ naagi-chat-app    │              │ naagi-category-   │                     │
│  │  (Port 8087)     │              │   admin (8085)   │                     │
│  │                  │              │                  │                     │
│  │  - Chat UI       │              │  - Category CRUD │                     │
│  │  - Session Mgmt  │              │  - Tool Config   │                     │
│  │  - History       │              │  - Doc Metadata  │                     │
│  └────────┬─────────┘              └────────┬─────────┘                     │
└───────────┼──────────────────────────────────┼───────────────────────────────┘
            │                                  │
            ▼                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ORCHESTRATION LAYER                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    naagi-ai-orchestrator                               │   │
│  │                         (Port 8086)                                   │   │
│  │                                                                       │   │
│  │  - LLM Tool Selection       - Intent Detection                       │   │
│  │  - Parameter Extraction     - Answer Generation                      │   │
│  │  - Confidence Routing       - Category Filtering                     │   │
│  │  - Own LLM client (self-contained)                                   │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CORE SERVICES                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │ naagi-mcp-       │  │ naagi-tool-      │  │ naagi-rag-       │              │
│  │  gateway (8082) │  │  registry (8081)│  │  service (8080) │              │
│  │                 │  │                 │  │                 │              │
│  │ - JSON-RPC 2.0  │  │ - Tool CRUD     │  │ - Ingest        │              │
│  │ - Tool Execution│◄─┤ - OpenAPI Parse │  │ - Query         │              │
│  │ - Registry Load │  │ - Schema Store  │  │ - Doc Parsing   │              │
│  │ - No built-ins  │  │                 │  │ - Own LLM       │              │
│  └────────┬────────┘  └─────────────────┘  └─────────────────┘              │
│           │                                                                  │
│           ▼                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    External Tool Endpoints                           │    │
│  │  - naagi-utility-tools (8083) - echo, add, time                      │    │
│  │  - Any registered OpenAPI endpoint                                   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Service Details

### 1. naagi-ai-orchestrator (Port 8086)

**Purpose**: Central intelligence layer for LLM-powered tool selection and routing

**Dependencies**: naagi-rag-service (8080), naagi-tool-registry (8081), naagi-mcp-gateway (8082), naagi-category-admin (8085)

**Responsibilities**:
- Receive user messages from chat-app
- Detect intent (tool call, RAG query, conversation)
- Select appropriate tool using LLM
- Extract and validate parameters
- Execute via mcp-gateway
- Generate natural language responses

**Structure**:
```
naagi-ai-orchestrator/
├── pom.xml
└── src/main/java/com/naag/orchestrator/
    ├── NaagOrchestratorApplication.java
    ├── config/
    │   ├── OrchestratorConfig.java
    │   └── LlmConfig.java
    ├── controller/
    │   └── OrchestratorController.java
    ├── service/
    │   ├── OrchestrationService.java      # Main orchestration logic
    │   ├── IntentDetectionService.java    # Detect user intent
    │   ├── ToolSelectionService.java      # LLM-based tool selection
    │   ├── ParameterExtractionService.java # Extract params from text
    │   ├── AnswerGenerationService.java   # Generate responses
    │   └── llm/
    │       ├── LlmClient.java             # LLM interface
    │       ├── OllamaClient.java          # Ollama implementation
    │       └── LlamaCppClient.java        # llama.cpp implementation
    ├── client/
    │   ├── McpGatewayClient.java          # Execute tools
    │   ├── ToolRegistryClient.java        # Get tool schemas
    │   ├── CategoryAdminClient.java       # Get category config
    │   └── RagServiceClient.java          # Direct RAG calls
    └── model/
        ├── OrchestrationRequest.java
        ├── OrchestrationResponse.java
        ├── Intent.java
        └── ToolSelection.java
```

**API**:
```
POST /api/orchestrate
Request:
{
  "message": "what is 5 plus 10?",
  "sessionId": "abc123",
  "categoryId": "general"
}

Response:
{
  "intent": "TOOL_CALL",
  "selectedTool": "add",
  "confidence": 0.95,
  "parameters": {"a": 5, "b": 10},
  "toolResult": {"result": 15},
  "response": "The sum of 5 and 10 is 15.",
  "reasoning": "User asked for arithmetic addition"
}
```

---

### 2. naagi-utility-tools (Port 8083)

**Purpose**: Provide basic utility tools as a separate service

**Dependencies**: naagi-tool-registry (8081)

**Responsibilities**:
- Echo messages
- Arithmetic operations
- Time/date queries
- Register itself with tool-registry on startup

**Structure**:
```
naagi-utility-tools/
├── pom.xml
└── src/main/java/com/naag/utilitytools/
    ├── NaagUtilityToolsApplication.java
    ├── config/
    │   └── ToolRegistrationConfig.java    # Auto-register on startup
    ├── controller/
    │   ├── EchoController.java            # POST /api/echo
    │   ├── CalculatorController.java      # POST /api/add, /api/subtract
    │   └── TimeController.java            # GET /api/time
    └── client/
        └── ToolRegistryClient.java        # Register tools
```

**APIs**:
```
POST /api/echo
Request: { "message": "hello" }
Response: { "echo": "hello" }

POST /api/add
Request: { "a": 5, "b": 10 }
Response: { "result": 15 }

GET /api/time
Response: { "time": "2025-01-17T10:30:00Z", "timezone": "UTC" }
```

---

## Core Services

### 3. naagi-mcp-gateway (Port 8082)

**Purpose**: MCP protocol gateway for tool execution

**Dependencies**: naagi-tool-registry (8081)

**Responsibilities**:
- Implement MCP JSON-RPC 2.0 protocol
- Load tools from tool-registry (no built-in tools)
- Execute tools via HTTP calls to registered endpoints
- Pure protocol handling

**Structure**:
```
naagi-mcp-gateway/
├── pom.xml
└── src/main/java/com/naag/mcpgateway/
    ├── NaagMcpGatewayApplication.java
    ├── config/
    │   └── McpConfig.java
    ├── controller/
    │   └── McpController.java             # POST /mcp/execute
    ├── service/
    │   ├── McpService.java                # MCP protocol handling
    │   └── ToolExecutionService.java      # Execute tools via HTTP
    ├── client/
    │   └── ToolRegistryClient.java        # Load tool definitions
    └── model/
        ├── McpRequest.java
        └── McpResponse.java
```

**Key Change**: Remove all built-in tools, load from registry only:
```java
@PostConstruct
public void initialize() {
    loadToolsFromRegistry();
}

@Scheduled(fixedRate = 60000)  // Refresh every minute
public void refreshTools() {
    loadToolsFromRegistry();
}
```

---

### 4. naagi-chat-app (Port 8087)

**Purpose**: User chat interface (thin UI layer)

**Dependencies**: naagi-ai-orchestrator (8086)

**Responsibilities**:
- Provide chat UI (REST and WebSocket)
- Manage chat sessions and history
- Delegate all intelligence to orchestrator

**Structure**:
```
naagi-chat-app/
├── pom.xml
└── src/main/java/com/naag/chatapp/
    ├── NaagChatAppApplication.java
    ├── controller/
    │   ├── ChatController.java            # REST endpoints
    │   └── ChatWebSocketHandler.java      # WebSocket streaming
    ├── service/
    │   ├── ChatService.java               # Session management
    │   └── MessageHistoryService.java     # Persistence
    ├── client/
    │   └── OrchestratorClient.java        # Call orchestrator
    └── model/
        ├── ChatSession.java
        ├── ChatMessage.java
        └── Conversation.java
```

**Simplified Logic**:
```java
public ChatMessage processMessage(String sessionId, String message, String categoryId) {
    // 1. Save user message
    saveMessage(sessionId, "user", message);

    // 2. Call orchestrator (all intelligence delegated)
    OrchestrationResponse result = orchestratorClient.orchestrate(
        new OrchestrationRequest(message, sessionId, categoryId)
    );

    // 3. Save and return response
    ChatMessage response = new ChatMessage("assistant", result.getResponse());
    saveMessage(sessionId, response);
    return response;
}
```

---

### 5. naagi-category-admin (Port 8085)

**Purpose**: Admin UI for categories, tools, and document metadata

**Dependencies**: naagi-rag-service (8080), naagi-tool-registry (8081)

**Responsibilities**:
- Category CRUD operations
- Tool-category mapping configuration
- Document metadata management (delegates parsing to RAG service)

**Structure**:
```
naagi-category-admin/
├── pom.xml
└── src/main/java/com/naag/categoryadmin/
    ├── NaagCategoryAdminApplication.java
    ├── controller/
    │   ├── CategoryController.java        # Category CRUD
    │   ├── ToolConfigController.java      # Tool-category mapping
    │   └── DocumentController.java        # Document metadata
    ├── service/
    │   ├── CategoryService.java           # Category management
    │   ├── ToolConfigService.java         # Tool configuration
    │   └── DocumentMetadataService.java   # Document references only
    ├── client/
    │   ├── RagServiceClient.java          # RAG API calls
    │   └── ToolRegistryClient.java        # Get available tools
    └── model/
        ├── Category.java
        ├── CategoryTool.java
        └── DocumentMetadata.java
```

**Document Upload Flow** (delegates parsing to RAG service):
```java
public void uploadDocument(MultipartFile file, String categoryId) {
    // 1. Call RAG service to parse and ingest
    IngestResult result = ragServiceClient.parseAndIngest(file, categoryId);

    // 2. Store metadata only
    DocumentMetadata metadata = new DocumentMetadata(
        result.getDocId(),
        file.getOriginalFilename(),
        categoryId
    );
    documentMetadataService.save(metadata);
}
```

---

### 6. naagi-rag-service (Port 8080)

**Purpose**: RAG operations with document parsing

**Dependencies**: None (foundational service)

**Responsibilities**:
- Document ingestion (text chunking, embedding generation)
- Semantic query with vector similarity
- Document parsing (PDF, Word, text)
- Own LLM client for embeddings

**Structure**:
```
naagi-rag-service/
├── pom.xml
└── src/main/java/com/naag/ragservice/
    ├── NaagRagServiceApplication.java
    ├── config/
    │   └── RagConfig.java
    ├── controller/
    │   └── RagController.java             # POST /api/rag/*
    ├── service/
    │   ├── RagService.java                # Ingest/query logic
    │   ├── DocumentParserService.java     # PDF/Word/text parsing
    │   ├── ChunkingService.java           # Text chunking
    │   └── EmbeddingService.java          # Vector embeddings
    └── model/
        ├── IngestRequest.java
        └── QueryResponse.java
```

**Key APIs**:
```java
POST /api/rag/ingest           // Ingest text
POST /api/rag/parse-and-ingest // Upload and ingest file
POST /api/rag/query            // Semantic search
```

**Document Parsing Dependencies** (pom.xml):
```xml
<!-- Apache PDFBox for PDF parsing -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.1</version>
</dependency>

<!-- Apache POI for Word document parsing -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

---

### 7. naagi-tool-registry (Port 8081)

**Purpose**: Register and manage tool definitions

**Dependencies**: None (foundational service)

**Responsibilities**:
- Tool CRUD operations
- OpenAPI spec parsing
- Tool schema storage
- Tool discovery endpoint

**Structure**:
```
naagi-tool-registry/
├── pom.xml
└── src/main/java/com/naag/toolregistry/
    ├── NaagToolRegistryApplication.java
    ├── controller/
    │   └── ToolController.java            # GET/POST /api/tools
    ├── service/
    │   ├── ToolService.java               # Tool CRUD
    │   └── OpenApiParserService.java      # Parse OpenAPI specs
    └── model/
        ├── Tool.java
        └── ToolSchema.java
```

**Key APIs**:
```java
GET  /api/tools            // List all tools
GET  /api/tools/{name}     // Get tool by name
POST /api/tools            // Register new tool
POST /api/tools/register   // Auto-register from OpenAPI spec
```

---

## Service Communication

### API Contracts

```
┌─────────────────┐   POST /api/orchestrate   ┌─────────────────────┐
│ naagi-chat-app   │ ─────────────────────────▶│ naagi-ai-orchestrator│
│ (8087)          │◀───────────────────────── │ (8086)              │
└─────────────────┘   OrchestrationResponse   └──────────┬──────────┘
                                                         │
                    ┌────────────────────────────────────┼────────────────────────────┐
                    │                                    │                            │
                    ▼                                    ▼                            ▼
         ┌──────────────────┐            ┌──────────────────┐          ┌──────────────────┐
         │ naagi-mcp-gateway │            │ naagi-tool-       │          │ naagi-rag-service │
         │ (8082)           │            │   registry (8081)│          │ (8080)           │
         │                  │            │                  │          │                  │
         │ POST /mcp/exec   │            │ GET /api/tools   │          │ POST /api/rag/*  │
         └────────┬─────────┘            └──────────────────┘          └──────────────────┘
                  │
                  ▼
         ┌──────────────────┐
         │ naagi-utility-    │
         │   tools (8083)   │
         │                  │
         │ POST /api/echo   │
         │ POST /api/add    │
         │ GET /api/time    │
         └──────────────────┘
```

### Communication Matrix

| From | To | Endpoint | Purpose |
|------|-----|----------|---------|
| naagi-chat-app | naagi-ai-orchestrator | POST /api/orchestrate | Process user message |
| naagi-ai-orchestrator | naagi-mcp-gateway | POST /mcp/execute | Execute tool |
| naagi-ai-orchestrator | naagi-tool-registry | GET /api/tools | Get tool schemas |
| naagi-ai-orchestrator | naagi-category-admin | GET /api/categories/{id}/enabled-tools | Get category config |
| naagi-ai-orchestrator | naagi-rag-service | POST /api/rag/query | Direct RAG query |
| naagi-mcp-gateway | naagi-tool-registry | GET /api/tools | Load tool definitions |
| naagi-mcp-gateway | naagi-utility-tools | POST /api/* | Execute utility tools |
| naagi-mcp-gateway | Any registered endpoint | HTTP | Execute registered tools |
| naagi-category-admin | naagi-rag-service | POST /api/rag/parse-and-ingest | Upload document |
| naagi-category-admin | naagi-tool-registry | GET /api/tools | List available tools |
| naagi-utility-tools | naagi-tool-registry | POST /api/tools/register | Self-registration |

---

## Port Assignments (Dependency Order)

| Port | Service | Dependencies | Description |
|------|---------|--------------|-------------|
| 8080 | naagi-rag-service | None | RAG operations + document parsing |
| 8081 | naagi-tool-registry | None | Tool definitions management |
| 8082 | naagi-mcp-gateway | 8081 | MCP protocol gateway |
| 8083 | naagi-utility-tools | 8081 | Basic tools: echo, add, time |
| 8085 | naagi-category-admin | 8080, 8081 | Category/tool configuration |
| 8086 | naagi-ai-orchestrator | 8080, 8081, 8082, 8085 | LLM tool selection & routing |
| 8087 | naagi-chat-app | 8086 | User chat interface |

Note: Port 8084 reserved for test services (not part of core platform).

---

## Implementation Phases

### Phase 1: Create foundational services (no dependencies)
1. Create naagi-rag-service (port 8080)
   - Copy from bkp/full-rag-springboot-sync
   - Rename packages to com.naagi.ragservice
   - Add document parsing (PDFBox, POI)

2. Create naagi-tool-registry (port 8081)
   - Copy from bkp/dynamic-tool-registry
   - Rename packages to com.naagi.toolregistry

### Phase 2: Create dependent core services
3. Create naagi-mcp-gateway (port 8082)
   - Copy from bkp/mcp-spring-boot-server
   - Remove built-in tools, load from registry only
   - Rename packages to com.naagi.mcpgateway

4. Create naagi-utility-tools (port 8083)
   - New Spring Boot project
   - Implement echo, add, time endpoints
   - Auto-register with tool-registry on startup

### Phase 3: Create admin and orchestration
5. Create naagi-category-admin (port 8085)
   - Copy from bkp/mcp-category-admin
   - Remove RAG/document parsing logic (delegate to RAG service)
   - Rename packages to com.naagi.categoryadmin

6. Create naagi-ai-orchestrator (port 8086)
   - New Spring Boot project
   - Copy LLM clients (self-contained)
   - Implement tool selection, intent detection, parameter extraction

### Phase 4: Create user interface
7. Create naagi-chat-app (port 8087)
   - Copy from bkp/mcp-chat-app
   - Simplify: remove all LLM logic, delegate to orchestrator
   - Rename packages to com.naagi.chatapp

---

## Benefits

1. **Clear Responsibilities**: Each service has one job
2. **Independent Deployment**: Services can be deployed separately
3. **Self-Contained**: No shared libraries, each service has its own code
4. **Easier Testing**: Mock HTTP calls, test services in isolation
5. **Scalability**: Scale orchestrator for LLM, gateway for throughput
6. **Flexibility**: Replace any service without affecting others

---

## Trade-offs

1. **Code Duplication**: LLM clients duplicated in orchestrator and rag-service
   - Acceptable: Different usage patterns, independent evolution

2. **Network Overhead**: More HTTP calls between services
   - Mitigated: Services on same network, fast local calls

3. **Complexity**: More services to manage
   - Mitigated: Clear responsibilities, easier to understand individually

---

## Monitoring

All services have Prometheus metrics enabled via Spring Boot Actuator:

| Service | Port | Metrics Endpoint |
|---------|------|-----------------|
| naagi-rag-service | 8080 | http://localhost:8080/actuator/prometheus |
| naagi-tool-registry | 8081 | http://localhost:8081/actuator/prometheus |
| naagi-mcp-gateway | 8082 | http://localhost:8082/actuator/prometheus |
| naagi-utility-tools | 8083 | http://localhost:8083/actuator/prometheus |
| naagi-category-admin | 8085 | http://localhost:8085/actuator/prometheus |
| naagi-ai-orchestrator | 8086 | http://localhost:8086/actuator/prometheus |
| naagi-chat-app | 8087 | http://localhost:8087/actuator/prometheus |

Prometheus scrape config:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'naagi-rag-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
        labels:
          application: 'naagi-rag-service'
    scrape_interval: 5s

  - job_name: 'naagi-tool-registry'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
        labels:
          application: 'naagi-tool-registry'
    scrape_interval: 5s

  - job_name: 'naagi-mcp-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8082']
        labels:
          application: 'naagi-mcp-gateway'
    scrape_interval: 5s

  - job_name: 'naagi-utility-tools'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8083']
        labels:
          application: 'naagi-utility-tools'
    scrape_interval: 5s

  - job_name: 'naagi-category-admin'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8085']
        labels:
          application: 'naagi-category-admin'
    scrape_interval: 5s

  - job_name: 'naagi-ai-orchestrator'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8086']
        labels:
          application: 'naagi-ai-orchestrator'
    scrape_interval: 5s

  - job_name: 'naagi-chat-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8087']
        labels:
          application: 'naagi-chat-app'
    scrape_interval: 5s
```
