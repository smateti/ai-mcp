# NAAG Platform Features Design

**NAAG = Nimbus AI Agent**

This document covers the architecture, component interactions, features, and user guides for the NAAG platform.

---

## Table of Contents

1. [Platform Architecture Overview](#1-platform-architecture-overview)
2. [Component Interaction Diagrams](#2-component-interaction-diagrams)
3. [Core Features](#3-core-features)
4. [FAQ Management System](#4-faq-management-system)
5. [Category Parameter Overrides](#5-category-parameter-overrides)
6. [User Questions & Analytics](#6-user-questions--analytics)
7. [Streaming Responses](#7-streaming-responses)
8. [CRAG & Reranking](#8-crag--reranking)
9. [Audit Logging System](#9-audit-logging-system)
10. [User Management](#10-user-management)
11. [Caching with EhCache](#11-caching-with-ehcache)
12. [Enhanced Document Upload with Q&A Preview](#12-enhanced-document-upload-with-qa-preview)
13. [Admin Manual](#13-admin-manual)
14. [User Manual](#14-user-manual)
15. [Configuration Summary](#15-configuration-summary)
16. [API Reference](#16-api-reference)

---

## 1. Platform Architecture Overview

### Service Topology

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              NAAG Platform Services                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐    │
│   │  naag-chat-app  │        │ naag-category-  │        │     Ollama      │    │
│   │    (8087)       │        │    admin (8085) │        │   LLM Server    │    │
│   │  User Interface │        │  Admin Interface│        │   (11434)       │    │
│   └────────┬────────┘        └────────┬────────┘        └────────┬────────┘    │
│            │                          │                          │             │
│            └──────────────┬───────────┴───────────┬──────────────┘             │
│                           │                       │                             │
│                           ▼                       ▼                             │
│            ┌──────────────────────────────────────────────────────┐            │
│            │            naag-ai-orchestrator (8086)               │            │
│            │         Central Intelligence & Routing               │            │
│            └──────────────────────────┬───────────────────────────┘            │
│                                       │                                        │
│            ┌──────────────────────────┼───────────────────────────┐            │
│            │                          │                           │            │
│            ▼                          ▼                           ▼            │
│   ┌────────────────┐       ┌──────────────────┐       ┌────────────────┐       │
│   │ naag-tool-     │       │ naag-mcp-gateway │       │ naag-rag-      │       │
│   │ registry (8081)│◄─────►│     (8082)       │       │ service (8080) │       │
│   │ Tool Metadata  │       │  MCP Protocol    │       │ Vector Store   │       │
│   └────────────────┘       └────────┬─────────┘       └───────┬────────┘       │
│                                     │                         │                │
│                                     ▼                         ▼                │
│                          ┌──────────────────┐       ┌──────────────────┐       │
│                          │ naag-utility-    │       │     Qdrant       │       │
│                          │   tools (8083)   │       │  Vector DB       │       │
│                          │ Built-in Tools   │       │   (6333)         │       │
│                          └──────────────────┘       └──────────────────┘       │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Service Port Map

| Service | Port | Purpose |
|---------|------|---------|
| naag-rag-service | 8080 | RAG operations, FAQ management, document storage, embeddings |
| naag-tool-registry | 8081 | Tool metadata, OpenAPI registration |
| naag-mcp-gateway | 8082 | MCP protocol, tool execution |
| naag-utility-tools | 8083 | Built-in tools (add, echo, time) |
| naag-category-admin | 8085 | Admin UI, category management, parameter overrides |
| naag-ai-orchestrator | 8086 | LLM routing, tool selection, streaming responses |
| naag-chat-app | 8087 | User chat interface |
| Ollama | 11434 | LLM inference |
| Qdrant | 6333 | Vector database (RAG chunks, FAQs, User Questions) |

---

## 2. Component Interaction Diagrams

### 2.1 Chat Message Flow with FAQ Check

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    Chat Message Processing Flow (with FAQ)                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  User                                                                           │
│   │                                                                             │
│   │ "What is dependency injection in Spring?"                                   │
│   ▼                                                                             │
│  ┌─────────────────────┐                                                        │
│  │   naag-chat-app     │  POST /api/orchestrator/stream                         │
│  └──────────┬──────────┘                                                        │
│             │                                                                   │
│             ▼                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐       │
│  │                    naag-ai-orchestrator (8086)                       │       │
│  │                                                                      │       │
│  │  1. Tool Selection → rag_query detected                              │       │
│  │                                                                      │       │
│  │  2. FAQ Check (before RAG)                                           │       │
│  │     ┌────────────────────────────────────────────────────────────┐  │       │
│  │     │ POST /api/faq-management/match-if-enabled                  │  │       │
│  │     │ → Check if FAQ exists for this question                    │  │       │
│  │     │ → If match found with score >= threshold, return FAQ answer│  │       │
│  │     └────────────────────────────────────────────────────────────┘  │       │
│  │                                                                      │       │
│  │  3. If no FAQ match → Execute full RAG pipeline                      │       │
│  │     • Retrieve relevant chunks from Qdrant                          │       │
│  │     • Generate answer with LLM                                      │       │
│  │     • Store user question for analytics                             │       │
│  │                                                                      │       │
│  │  4. Stream response via SSE                                          │       │
│  └──────────────────────────────────────────────────────────────────────┘       │
│                                                                                 │
│  Response (streamed): "Dependency injection is a design pattern..."             │
│  Source: [FAQ Cache] or [RAG + Sources]                                         │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Tool Execution with Locked Parameters

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│               Tool Execution with Category Parameter Overrides                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  User (in "Service Development" category)                                       │
│   │                                                                             │
│   │ "What services does APP-USER-MGMT have?"                                    │
│   ▼                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐       │
│  │                    naag-ai-orchestrator (8086)                       │       │
│  │                                                                      │       │
│  │  1. Get merged tools from category-admin                             │       │
│  │     GET /api/categories/service-development/tools/merged             │       │
│  │                                                                      │       │
│  │  2. Tool Selection: getApplicationServicesById                       │       │
│  │     LLM extracts: appType=?, applicationId=APP-USER-MGMT             │       │
│  │                                                                      │       │
│  │  3. Apply Locked Parameters                                          │       │
│  │     ┌────────────────────────────────────────────────────────────┐  │       │
│  │     │ Category Override: appType LOCKED to "MICROSERVICE"        │  │       │
│  │     │ → Force appType=MICROSERVICE (ignore LLM extraction)       │  │       │
│  │     └────────────────────────────────────────────────────────────┘  │       │
│  │                                                                      │       │
│  │  4. Execute tool via MCP Gateway                                     │       │
│  │     Parameters: { appType: "MICROSERVICE", applicationId: "..." }   │       │
│  │                                                                      │       │
│  │  5. Handle result                                                    │       │
│  │     • Success → Format and stream response                          │       │
│  │     • Empty/404 with locked params → Show constraint message        │       │
│  └──────────────────────────────────────────────────────────────────────┘       │
│                                                                                 │
│  If app is BATCH type but category locks to MICROSERVICE:                       │
│  "I couldn't find any results. Note: This category has constraints:             │
│   - App Type is set to MICROSERVICE                                             │
│   Try asking about a different application or switch categories."               │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Service Communication Matrix

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        Service Communication Matrix                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  From ↓ / To →    │ RAG  │ Tool │ MCP  │ Util │ Admin│ Orch │ Chat │ Ollama    │
│                   │ 8080 │ 8081 │ 8082 │ 8083 │ 8085 │ 8086 │ 8087 │ 11434     │
│  ─────────────────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┼───────    │
│  naag-rag-service │  -   │  -   │  -   │  -   │  -   │  -   │  -   │  ✓        │
│  (8080)           │      │      │      │      │      │      │      │ embed/gen │
│  ─────────────────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┼───────    │
│  naag-tool-reg    │  -   │  -   │  -   │  -   │  -   │  -   │  -   │  -        │
│  (8081)           │      │      │      │      │      │      │      │           │
│  ─────────────────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┼───────    │
│  naag-mcp-gateway │  ✓   │  ✓   │  -   │  ✓   │  -   │  -   │  -   │  -        │
│  (8082)           │ RAG  │tools │      │tools │      │      │      │           │
│  ─────────────────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┼───────    │
│  naag-category    │  ✓   │  ✓   │  -   │  -   │  -   │  -   │  -   │  -        │
│  admin (8085)     │ mgmt │ mgmt │      │      │      │      │      │           │
│  ─────────────────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┼───────    │
│  naag-ai-orch     │  ✓   │  -   │  ✓   │  -   │  ✓   │  -   │  -   │  ✓        │
│  (8086)           │FAQ,Q │      │exec  │      │merged│      │      │ select    │
│  ─────────────────┼──────┼──────┼──────┼──────┼──────┼──────┼──────┼───────    │
│  naag-chat-app    │  -   │  -   │  -   │  -   │  -   │  ✓   │  -   │  -        │
│  (8087)           │      │      │      │      │      │stream│      │           │
│                                                                                 │
│  Legend: ✓ = HTTP REST calls, - = No direct communication                       │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Features

### Feature Summary

| Feature | Description | Service |
|---------|-------------|---------|
| **RAG Query** | Semantic search over documents with LLM answer generation | naag-rag-service |
| **FAQ Cache** | Pre-approved Q&A pairs for instant responses | naag-rag-service |
| **Reranking** | Two-stage retrieval with cross-encoder for higher precision | naag-rag-service |
| **CRAG** | Corrective RAG - evaluates confidence and applies fallback strategies | naag-rag-service |
| **Tool Execution** | Execute registered tools via MCP protocol | naag-mcp-gateway |
| **Category Management** | Organize tools and documents by category | naag-category-admin |
| **Parameter Overrides** | Lock tool parameters per category | naag-category-admin |
| **User Question Analytics** | Track and analyze user questions | naag-rag-service |
| **Document Preview** | Q&A validation before adding to RAG | naag-rag-service |
| **Streaming Responses** | Real-time SSE streaming for chat | naag-ai-orchestrator |
| **Audit Logging** | Complete interaction history | All services |

### Qdrant Collections

| Collection | Purpose | Key Payload Fields |
|------------|---------|-------------------|
| `naag_documents` | RAG document chunks | docId, categoryId, text, chunkIndex |
| `naag_faq` | Approved FAQ pairs | faqId, question, answer, categoryId |
| `naag_user_questions` | User question tracking | question, categoryId, frequency, lastAsked |

---

## 4. FAQ Management System

### Overview

The FAQ system provides instant answers for frequently asked questions without requiring full RAG processing. FAQs can be created from:
1. Document Q&A approval workflow
2. Promoting user questions to FAQs
3. Direct admin creation

### FAQ Workflow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              FAQ Management Flow                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  SOURCE 1: Document Upload                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ Upload Document → Generate Q&A → Admin Review → Approve → Add to FAQ    │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│  SOURCE 2: User Questions                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ User asks question → Stored in Qdrant → Admin reviews frequent          │   │
│  │ questions → Promotes to FAQ with curated answer                         │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│  QUERY FLOW:                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ User Question                                                            │   │
│  │      │                                                                   │   │
│  │      ▼                                                                   │   │
│  │ ┌──────────────────┐     ┌───────────────────────────────────────────┐  │   │
│  │ │ Check FAQ Cache  │────►│ Match found (score >= 0.85)?              │  │   │
│  │ └──────────────────┘     │   YES → Return FAQ answer instantly       │  │   │
│  │                          │   NO  → Execute full RAG pipeline         │  │   │
│  │                          └───────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### FAQ Database Schema

**faq_entries table:**

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| categoryId | VARCHAR | Category this FAQ belongs to |
| docId | VARCHAR | Source document (or "USER_QUESTION") |
| uploadId | VARCHAR | Source upload ID (nullable) |
| questionType | VARCHAR | FINE_GRAIN, SUMMARY, or USER_PROMOTED |
| question | CLOB | The question text |
| answer | CLOB | The approved answer |
| similarityScore | DOUBLE | Validation score (from Q&A validation) |
| qdrantPointId | VARCHAR | Qdrant vector ID for deletion |
| active | BOOLEAN | Soft delete flag |
| accessCount | INT | How many times this FAQ was used |
| lastAccessedAt | TIMESTAMP | Last time FAQ was returned |
| createdAt | TIMESTAMP | Creation timestamp |

### FAQ Settings

| Setting | Default | Description |
|---------|---------|-------------|
| faqQueryEnabled | true | Master toggle for FAQ matching |
| minSimilarityScore | 0.85 | Minimum score for FAQ match |
| storeUserQuestions | true | Store user questions for analytics |
| autoSelectThreshold | 0.70 | Auto-select Q&A with score >= this |

---

## 5. Category Parameter Overrides

### Overview

Category parameter overrides allow the same tool to behave differently in different categories by locking or restricting parameter values.

### Use Cases

1. **App Type Restriction**: In "Service Development" category, lock `appType` to "MICROSERVICE"
2. **Environment Lock**: In "Production" category, lock `environment` to "prod"
3. **Enum Restriction**: Limit allowed values for a parameter in specific categories

### Override Data Model

**category_parameter_overrides table:**

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| categoryId | VARCHAR | FK to categories |
| toolId | VARCHAR | Tool identifier |
| parameterPath | VARCHAR | Parameter name (e.g., "appType") |
| humanReadableDescription | VARCHAR | Override description |
| example | VARCHAR | Category-specific example |
| enumValues | VARCHAR | Restricted allowed values (comma-separated) |
| lockedValue | VARCHAR | Pre-filled, read-only value |
| active | BOOLEAN | Is this override active |

### Override Precedence

```
1. Locked Value (highest) - Parameter pre-filled, cannot be changed
2. Enum Override - Restricts allowed values
3. Example Override - Category-specific example
4. Description Override - Category-specific description
5. Base Value (lowest) - Original from tool-registry
```

### Merged Tool Response

```json
{
  "toolId": "getAppInfo",
  "parameters": [{
    "name": "appType",
    "locked": true,
    "lockedValue": "MICROSERVICE",
    "enumValues": "MICROSERVICE",
    "effectiveDescription": "Application type. [LOCKED to: MICROSERVICE]",
    "enumOverridden": false,
    "descriptionOverridden": true
  }]
}
```

---

## 6. User Questions & Analytics

### Overview

All user questions are stored in Qdrant for:
- Frequency analysis
- Identifying FAQ candidates
- Understanding user needs
- Deduplication

### User Question Flow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         User Question Analytics                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  User asks: "How do I configure Spring Security?"                               │
│       │                                                                         │
│       ▼                                                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ 1. Generate embedding for question                                   │      │
│  │ 2. Search existing questions (dedup check, threshold: 0.95)          │      │
│  │    - If similar exists → Increment frequency                         │      │
│  │    - If new → Store new question                                     │      │
│  │ 3. Continue with RAG/FAQ processing                                  │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
│  Admin Dashboard:                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ Top Questions by Frequency                                           │      │
│  │ ┌──────────────────────────────────────────────┬──────────┬────────┐ │      │
│  │ │ Question                                     │ Frequency│ Action │ │      │
│  │ ├──────────────────────────────────────────────┼──────────┼────────┤ │      │
│  │ │ How do I configure Spring Security?         │ 47       │[Promote]│ │      │
│  │ │ What is dependency injection?               │ 35       │[Promote]│ │      │
│  │ │ How to create REST endpoints?               │ 28       │[Promote]│ │      │
│  │ └──────────────────────────────────────────────┴──────────┴────────┘ │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Similar FAQ Detection

When viewing an FAQ, admins can find similar FAQs (90%+ similarity) to:
- Merge duplicate answers
- Ensure consistency
- Clean up redundant entries

---

## 7. Streaming Responses

### Overview

All chat responses are streamed via Server-Sent Events (SSE) for real-time feedback.

### SSE Event Types

| Event | Description |
|-------|-------------|
| `message` | Text token chunk |
| `tool` | Tool execution started |
| `source` | RAG source document |
| `done` | Response complete |
| `error` | Error occurred |

### Streaming Flow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           SSE Streaming Response                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Client                           Server (Orchestrator)                         │
│    │                                    │                                       │
│    │ GET /api/orchestrator/stream ─────►│                                       │
│    │ (SSE Connection)                   │                                       │
│    │                                    │                                       │
│    │◄── event: message                  │                                       │
│    │    data: "Dependency "             │                                       │
│    │                                    │                                       │
│    │◄── event: message                  │                                       │
│    │    data: "injection is "           │                                       │
│    │                                    │                                       │
│    │◄── event: message                  │                                       │
│    │    data: "a design pattern..."     │                                       │
│    │                                    │                                       │
│    │◄── event: source                   │                                       │
│    │    data: {"doc": "spring.md"}      │                                       │
│    │                                    │                                       │
│    │◄── event: done                     │                                       │
│    │    data: {}                        │                                       │
│    │                                    │                                       │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. CRAG & Reranking

### Overview

The NAAG RAG service implements advanced retrieval enhancement techniques to improve answer quality:

1. **Reranking (Two-Stage Retrieval)**: Initial retrieval returns many candidates, then a cross-encoder model re-scores them for higher precision
2. **CRAG (Corrective RAG)**: Evaluates retrieval quality and applies corrective strategies when confidence is low

### Reranking Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         Two-Stage Retrieval with Reranking                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  User Query: "How do I configure Spring Security?"                              │
│       │                                                                         │
│       ▼                                                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ Stage 1: Initial Retrieval (Fast, Approximate)                       │      │
│  │   • Embed query → Search Qdrant → Return top 50 candidates           │      │
│  │   • Uses bi-encoder embeddings (fast but less precise)               │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│       │                                                                         │
│       │ 50 candidate documents                                                  │
│       ▼                                                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ Stage 2: Reranking (Slow, Precise)                                   │      │
│  │   • Cross-encoder scores each (query, document) pair                 │      │
│  │   • Much higher precision than bi-encoder                            │      │
│  │   • Returns top 5 after re-scoring                                   │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│       │                                                                         │
│       │ 5 best documents (re-ranked)                                            │
│       ▼                                                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ Generate Answer with LLM                                             │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Reranking Providers

| Provider | Description | Use Case |
|----------|-------------|----------|
| `local` | Local cross-encoder via llama.cpp `/rerank` endpoint | Self-hosted, fast |
| `cohere` | Cohere Rerank API | High quality, commercial |
| `jina` | Jina Reranker API | Multilingual support |
| `llm` | Use LLM for scoring | Flexible, slower |

### Reranking Configuration

```yaml
naag:
  rag:
    rerank:
      enabled: true                    # Enable two-stage retrieval
      provider: local                  # local, cohere, jina, or llm
      base-url: http://localhost:8001  # Local reranker server
      model: bge-reranker-v2-m3        # BGE multilingual reranker
      api-key:                         # API key for Cohere/Jina
      candidate-count: 50              # Initial retrieval count
      min-score: 0.0                   # Minimum rerank score to include
```

### CRAG (Corrective RAG)

CRAG evaluates retrieval quality and applies corrective strategies based on confidence levels.

#### Confidence Categories

| Category | Confidence | Action |
|----------|------------|--------|
| **CORRECT** | ≥ 0.85 | Use retrieved documents directly |
| **AMBIGUOUS** | 0.65 - 0.85 | Refine context, add uncertainty markers |
| **INCORRECT** | < 0.65 | Apply fallback strategies |

#### CRAG Flow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Corrective RAG (CRAG) Flow                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  User Query                                                                     │
│       │                                                                         │
│       ▼                                                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ 1. Initial Retrieval + Reranking                                     │      │
│  │    → Get top-K documents                                             │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│       │                                                                         │
│       ▼                                                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ 2. Evaluate Confidence                                               │      │
│  │    • Top score analysis                                              │      │
│  │    • Score distribution (variance, gaps)                             │      │
│  │    • Optional LLM verification for borderline cases                  │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│       │                                                                         │
│       ├──────────────────────────────────────────────────────────────────┐     │
│       │                                                                  │     │
│       ▼                                    ▼                             ▼     │
│  ┌──────────┐                     ┌──────────────┐              ┌───────────┐  │
│  │ CORRECT  │                     │  AMBIGUOUS   │              │ INCORRECT │  │
│  │ (≥0.85)  │                     │ (0.65-0.85)  │              │  (<0.65)  │  │
│  └────┬─────┘                     └──────┬───────┘              └─────┬─────┘  │
│       │                                  │                            │        │
│       │                                  ▼                            ▼        │
│       │                     ┌────────────────────────┐   ┌─────────────────┐   │
│       │                     │ Knowledge Refinement   │   │ Query Expansion │   │
│       │                     │ • Re-score chunks      │   │ • Generate 2-3  │   │
│       │                     │ • Filter low relevance │   │   alternative   │   │
│       │                     │ • Add uncertainty      │   │   queries       │   │
│       │                     │   marker to answer     │   │ • Retry search  │   │
│       │                     └────────────────────────┘   │ • Merge results │   │
│       │                                                  └─────────────────┘   │
│       │                                                                        │
│       ▼                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ 3. Generate Answer (with confidence-based prompting)                 │      │
│  │    • CORRECT: Answer confidently                                     │      │
│  │    • AMBIGUOUS: Add "Based on available information..." prefix       │      │
│  │    • INCORRECT: Add disclaimer or refuse if too low                  │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
│  Note: If top relevance score < min-relevance-for-answer (0.75),               │
│        refuse to generate answer to prevent hallucination.                      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### CRAG Strategies

| Strategy | When Applied | Description |
|----------|--------------|-------------|
| **Direct Use** | CORRECT | Use retrieved documents without modification |
| **Knowledge Refinement** | AMBIGUOUS | Re-score and filter chunks for better relevance |
| **Uncertainty Marker** | AMBIGUOUS | Prefix answer with "Based on available information..." |
| **Query Expansion** | INCORRECT | Generate alternative queries and retry search |
| **Source Merging** | INCORRECT | Combine results from original + expanded queries |
| **Low Relevance Refusal** | Very Low | Refuse to answer if top score < threshold |

#### CRAG Configuration

```yaml
naag:
  rag:
    crag:
      enabled: true                          # Enable CRAG evaluation
      high-confidence-threshold: 0.85        # Above = CORRECT
      low-confidence-threshold: 0.65         # Below = INCORRECT
      score-gap-threshold: 0.15              # Large gap to 2nd = more confident
      llm-evaluation-enabled: false          # Use LLM for borderline cases
      query-expansion-enabled: true          # Expand queries when low confidence
      knowledge-refinement-enabled: true     # Refine/filter chunks
      max-retry-attempts: 2                  # Max query expansion retries
      min-relevance-for-answer: 0.75         # Refuse answer if top score below
```

### Evaluation Metrics

The RetrievalEvaluator computes confidence using multiple signals:

| Metric | Weight | Description |
|--------|--------|-------------|
| Top Score | 70% | Highest relevance score from retrieval |
| Gap Bonus | +10% max | Large gap between 1st and 2nd result |
| Variance Penalty | -20% max | High variance indicates inconsistent results |
| Consistency Bonus | +5% | All results have high, similar scores |
| Count Penalty | -5% | Too few results may miss relevant docs |

### CRAG Response Examples

**CORRECT Confidence:**
```
Dependency injection is a design pattern where objects receive their
dependencies from external sources rather than creating them internally.
In Spring, this is achieved through @Autowired annotation...
```

**AMBIGUOUS Confidence:**
```
Based on the available information: The application appears to use
Spring Security for authentication. The specific configuration details
may vary depending on your setup.
```

**INCORRECT / Low Relevance:**
```
I don't have specific information about that in the knowledge base.
The retrieved documents discuss related topics but don't directly
address your question.
```

### Performance Tuning

| Scenario | Recommended Settings |
|----------|---------------------|
| **High Quality** | candidate-count: 100, rerank: enabled, CRAG: enabled |
| **Fast Response** | candidate-count: 20, rerank: disabled, CRAG: disabled |
| **Balanced** | candidate-count: 50, rerank: enabled, CRAG: enabled (default) |
| **Strict Accuracy** | min-relevance-for-answer: 0.80, llm-evaluation: true |

---

## 9. Audit Logging System

### Overview

All user interactions are logged for traceability, analytics, and debugging.

### Audit Log Schema

| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT | Auto-increment primary key |
| timestamp | TIMESTAMP | When the action occurred |
| userId | VARCHAR | User identifier |
| userRole | VARCHAR | ADMIN or USER |
| sessionId | VARCHAR | Session identifier |
| serviceName | VARCHAR | Which NAAG service |
| actionType | VARCHAR | CHAT, TOOL_CALL, RAG_QUERY, etc. |
| question | TEXT | User's input |
| answer | TEXT | System's response |
| toolName | VARCHAR | Tool executed (if applicable) |
| toolParameters | TEXT | JSON parameters |
| status | VARCHAR | SUCCESS, FAILED, PARTIAL, TIMEOUT |
| durationMs | BIGINT | Processing time |
| confidence | DECIMAL | LLM confidence score |

### Action Types

| Type | Description |
|------|-------------|
| CHAT | Regular chat message |
| CHAT_STREAM | Streaming chat response |
| TOOL_CALL | Tool execution |
| TOOL_SELECTION | LLM tool selection |
| RAG_QUERY | Knowledge base query |
| FAQ_MATCH | FAQ cache hit |
| RAG_INGEST | Document ingestion |
| ADMIN_* | Administrative actions |

---

## 10. User Management

### User Roles

| Role | Permissions |
|------|-------------|
| **USER** | Chat interface, category selection, view own history |
| **ADMIN** | All USER permissions + category/tool/document management, audit logs, settings |

### User Context Headers

| Header | Purpose | Default |
|--------|---------|---------|
| X-User-Id | User identifier | system |
| X-User-Role | ADMIN or USER | USER |
| X-Session-Id | Session tracking | auto-generated |

---

## 11. Caching with EhCache

### Cache Configuration

| Cache Name | Purpose | TTL | Heap Size |
|------------|---------|-----|-----------|
| tools | Tool definitions | 60s | 500 |
| categories | Category list | 60s | 100 |
| categoryTools | Tools per category | 60s | 100 |
| ragQueryResults | RAG query results | 300s | 200 |
| embeddings | Embedding vectors | 600s | 1000 |
| faqCache | FAQ matches | 300s | 500 |

---

## 12. Enhanced Document Upload with Q&A Preview

### Processing Status States

| Status | Description |
|--------|-------------|
| PENDING | Upload received, waiting to process |
| GENERATING_QA | AI generating Q&A pairs |
| CHUNKING_TEMP | Creating temp collection chunks |
| VALIDATING_QA | Testing RAG answers |
| READY_FOR_REVIEW | Processing complete, admin review needed |
| APPROVED | Admin approved, moving to RAG |
| MOVED_TO_RAG | In knowledge base |
| FAILED | Processing error |

### Q&A Review Screen

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Document: product-manual-v1.md                    Status: READY_FOR_REVIEW     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Validation Stats:  ✅ Passed: 6  |  ❌ Failed: 2  |  📊 Average: 78%            │
│                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────┐ │
│  │ ☐ │ Type     │ Question              │ Score │ Status │ Select for FAQ   │ │
│  ├───┼──────────┼───────────────────────┼───────┼────────┼──────────────────┤ │
│  │ ☑ │ Detail   │ What is the max...    │ 92%   │ ✅     │ [Expected ▼]     │ │
│  │ ☐ │ Detail   │ How do you...         │ 45%   │ ❌     │ [None ▼]         │ │
│  │ ☑ │ Summary  │ Main purpose of...    │ 88%   │ ✅     │ [RAG Answer ▼]   │ │
│  │ ☑ │ Detail   │ Configuration for...  │ 95%   │ ✅     │ [Edited ▼]       │ │
│  └───────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
│  [Auto-Select Passed] [✅ Approve Selected FAQs] [Move to RAG] [🗑️ Delete]      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 13. Admin Manual

### Getting Started

1. **Access Admin UI**: Navigate to `http://localhost:8085`
2. **Dashboard**: View service health, statistics, and quick actions

### Category Management

#### Create a Category
1. Go to **Categories** tab
2. Click **+ New Category**
3. Fill in:
   - **ID**: Unique identifier (e.g., "service-development")
   - **Name**: Display name (e.g., "Service Development")
   - **Description**: Category purpose
4. Click **Save**

#### Assign Tools to Category
1. Open category details
2. Go to **Tools** tab
3. Check tools to include
4. Click **Save Changes**

### Tool Management

#### Register Tool from OpenAPI
1. Go to **Tools** tab
2. Click **+ Register from OpenAPI**
3. Enter OpenAPI URL (e.g., `http://service:8080/v3/api-docs`)
4. Click **Fetch & Parse**
5. Review extracted tools
6. Assign to categories
7. Click **Register**

#### Configure Parameter Overrides
1. Open category details
2. Go to **Tool Overrides** tab
3. Select a tool
4. For each parameter:
   - **Lock Value**: Set a fixed value users can't change
   - **Restrict Enum**: Limit allowed values
   - **Custom Description**: Category-specific help text
5. Click **Save Overrides**

### Document Management

#### Upload Document
1. Go to **Documents** tab
2. Click **+ Upload Document**
3. Fill in:
   - **Document ID**: Unique identifier
   - **Title**: Display title
   - **Category**: Target category
   - **Content**: Paste or upload content
4. Click **Upload & Process**
5. Wait for Q&A generation (SSE updates shown)
6. Review generated Q&A pairs
7. Select pairs to approve as FAQs
8. Click **Approve & Move to RAG**

### FAQ Management

#### View FAQs
1. Go to **FAQs** tab
2. Filter by category, search text
3. View access counts, last accessed

#### Promote User Question to FAQ
1. Go to **User Questions** tab
2. Sort by frequency
3. Click **Promote** on a question
4. Edit/improve the answer
5. Click **Create FAQ**

#### Find Similar FAQs
1. Open an FAQ
2. Click **Find Similar**
3. Review FAQs with 90%+ similarity
4. Select duplicates to merge
5. Click **Merge Answers**

#### Cleanup Stale FAQs
If a deleted FAQ still appears in search:
1. Go to **FAQs** → **Admin** tab
2. Enter the FAQ ID
3. Click **Cleanup from Qdrant**

### Settings

#### FAQ Settings
- **Enable FAQ Query**: Master toggle for FAQ matching
- **Min Similarity Score**: Threshold for FAQ match (default: 0.85)
- **Store User Questions**: Track user questions for analytics
- **Auto-Select Threshold**: Auto-select Q&A pairs above this score

### Monitoring

#### Service Health
- Dashboard shows all service status
- Green = UP, Red = DOWN
- Response times tracked

#### Cache Management
- View cache statistics
- Clear specific caches
- Clear all caches

---

## 14. User Manual

### Getting Started

1. **Access Chat**: Navigate to `http://localhost:8087`
2. **Select Category**: Choose the appropriate category from the dropdown

### Using the Chat

#### Ask Questions
- Type your question naturally
- Examples:
  - "What is dependency injection in Spring?"
  - "How do I create a REST endpoint?"
  - "What services does APP-001 have?"

#### Understanding Responses

**FAQ Response** (Instant):
```
📚 [From FAQ Cache]
Dependency injection is a design pattern where...
```

**RAG Response** (With sources):
```
Dependency injection is a design pattern where...

📖 Sources:
• spring-guide.md (section 3.2)
• di-patterns.pdf (page 12)
```

**Tool Response**:
```
The application APP-001 has the following services:
• UserService (REST API)
• NotificationService (Message Queue)

🔧 Tool: getApplicationServices | ⏱️ 234ms
```

### Category Constraints

Some categories have locked parameters. If you ask about something outside the category scope, you'll see:

```
I couldn't find any results for your query.

Note: This category has the following constraints:
- App Type is set to MICROSERVICE

Try asking about a different application, or switch to a different category.
```

### Tips

1. **Be Specific**: Include application IDs, version numbers, etc.
2. **Use Right Category**: Switch categories for different topics
3. **Check Sources**: RAG responses include source documents
4. **Streaming**: Responses appear word-by-word in real-time

---

## 15. Configuration Summary

### application.yml Template

```yaml
spring:
  application:
    name: naag-service-name

  # H2 Database
  datasource:
    url: jdbc:h2:file:./data/naag-db;AUTO_SERVER=TRUE

  # JCache/EhCache
  cache:
    type: jcache
    jcache:
      config: classpath:ehcache.xml

naag:
  # Audit
  audit:
    enabled: true
    storage: h2

  # Cache
  cache:
    enabled: true
    default-ttl-seconds: 60

  # RAG Settings
  rag:
    min-relevance-score: 0.65
    qa-generation:
      fine-grain-count: 5
      summary-count: 3
      validation-similarity-threshold: 0.7

    # Hybrid Search (optional)
    hybrid:
      enabled: false
      dense-weight: 0.7
      sparse-weight: 0.3
      rrf-k: 60

    # Reranking - Two-stage retrieval
    rerank:
      enabled: true
      provider: local                  # local, cohere, jina, llm
      base-url: http://localhost:8001
      model: bge-reranker-v2-m3
      candidate-count: 50
      min-score: 0.0

    # CRAG - Corrective RAG
    crag:
      enabled: true
      high-confidence-threshold: 0.85
      low-confidence-threshold: 0.65
      score-gap-threshold: 0.15
      llm-evaluation-enabled: false
      query-expansion-enabled: true
      knowledge-refinement-enabled: true
      max-retry-attempts: 2
      min-relevance-for-answer: 0.75

  # FAQ Settings
  faq:
    enabled: true
    collection: naag_faq
    min-similarity-score: 0.85
    auto-select-threshold: 0.7

  # User Questions
  user-questions:
    enabled: true
    collection: naag_user_questions
    deduplication-threshold: 0.95
    store-all-questions: true

  # Qdrant
  qdrant:
    baseUrl: http://localhost:6333
    collection: naag_documents
    vectorSize: 768
```

---

## 16. API Reference

### RAG Service (8080)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/rag/query` | POST | Execute RAG query |
| `/api/rag/query/stream` | POST | Stream RAG query (SSE) |
| `/api/documents/ingest` | POST | Ingest document |
| `/api/documents/upload` | POST | Upload with Q&A preview |

### FAQ Management (8080)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/faq-management` | GET | List FAQs (paginated) |
| `/api/faq-management/{id}` | GET | Get FAQ by ID |
| `/api/faq-management/{id}` | PUT | Update FAQ |
| `/api/faq-management/{id}` | DELETE | Deactivate FAQ |
| `/api/faq-management/query` | POST | Search FAQs |
| `/api/faq-management/match-if-enabled` | POST | Find best match |
| `/api/faq-management/{id}/similar` | GET | Find similar FAQs |
| `/api/faq-management/merge` | POST | Merge FAQs |
| `/api/faq-management/settings` | GET/PUT | FAQ settings |
| `/api/faq-management/qdrant/cleanup/{id}` | DELETE | Cleanup stale FAQ |

### Category Admin (8085)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/categories` | GET/POST | List/create categories |
| `/api/categories/{id}` | GET/PUT/DELETE | Category CRUD |
| `/api/categories/{id}/tools` | GET/PUT | Category tools |
| `/api/categories/{id}/tools/merged` | GET | Get tools with overrides |
| `/api/categories/{id}/tools/{toolId}/overrides` | GET/POST | Parameter overrides |

### Tool Registry (8081)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/tools` | GET | List all tools |
| `/api/tools/{id}` | GET | Get tool by ID |
| `/api/tools/register-openapi` | POST | Register from OpenAPI |
| `/api/tools/by-tool-id/{toolId}` | DELETE | Delete by toolId |

### Orchestrator (8086)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/orchestrator/process` | POST | Process message (non-streaming) |
| `/api/orchestrator/stream` | POST | Process message (SSE streaming) |

---

## Summary

The NAAG platform provides a comprehensive AI-powered knowledge and tool orchestration system with:

- **Smart Query Routing**: FAQ cache → RAG → Tool execution
- **Advanced Retrieval**: Two-stage retrieval with cross-encoder reranking for precision
- **Corrective RAG (CRAG)**: Confidence evaluation with query expansion and knowledge refinement
- **Category-Based Context**: Tools and documents organized by use case
- **Parameter Control**: Lock/restrict tool parameters per category
- **Analytics**: User question tracking and FAQ promotion
- **Real-Time**: SSE streaming for responsive chat
- **Admin Control**: Full management UI for categories, tools, documents, FAQs
- **Observability**: Audit logging, cache stats, service health

### Quick Reference

| Component | Port | Primary Purpose |
|-----------|------|-----------------|
| Chat UI | 8087 | User interface |
| Admin UI | 8085 | Management interface |
| Orchestrator | 8086 | Request routing & streaming |
| RAG Service | 8080 | Document search & FAQ |
| Tool Registry | 8081 | Tool metadata |
| MCP Gateway | 8082 | Tool execution |
| Qdrant | 6333 | Vector storage |
| Ollama | 11434 | LLM inference |
