# NAAG Administrator Manual

**NAAG = Nimbus AI Agent Platform**

This manual provides comprehensive guidance for administrators managing the NAAG platform.

---

## Table of Contents

1. [Getting Started](#1-getting-started)
2. [Admin Dashboard Overview](#2-admin-dashboard-overview)
3. [Category Management](#3-category-management)
4. [Tool Management](#4-tool-management)
5. [Document Management](#5-document-management)
6. [Enhanced Document Upload with Q&A Preview](#6-enhanced-document-upload-with-qa-preview)
7. [Audit Logs](#7-audit-logs)
8. [Cache Management](#8-cache-management)
9. [Service Health Monitoring](#9-service-health-monitoring)
10. [System Settings](#10-system-settings)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Getting Started

### Accessing the Admin Interface

The NAAG Admin interface is available at:

```
http://localhost:8085
```

### Administrator Permissions

As an administrator, you have full access to:

| Feature | Description |
|---------|-------------|
| Category Management | Create, edit, delete categories |
| Tool Management | Register, configure, remove tools |
| Document Management | Upload, approve, delete documents |
| Audit Logs | View all user activity |
| Cache Management | Clear and monitor caches |
| Service Health | Monitor all NAAG services |
| System Settings | Configure platform settings |

### Prerequisites

Before using the admin interface, ensure:

1. All NAAG services are running (check Service Health dashboard)
2. Ollama LLM server is running on port 11434
3. Qdrant vector database is running on port 6333
4. You have ADMIN role privileges

---

## 2. Admin Dashboard Overview

### Dashboard Metrics

The main dashboard displays:

| Metric | Description |
|--------|-------------|
| Categories | Total number of categories |
| Tools | Total registered tools |
| Documents | Total documents in RAG |
| Active Users | Currently active users |

### Service Health Panel

Monitor the health of all NAAG services:

| Status | Meaning |
|--------|---------|
| Green (UP) | Service is healthy |
| Red (DOWN) | Service is unavailable |
| Yellow (UNKNOWN) | Cannot determine status |

### Quick Actions

From the dashboard, you can:

- Navigate to any management section
- View recent activity
- Access system settings
- Clear all caches

---

## 3. Category Management

### What are Categories?

Categories organize tools and documents into logical groups. Users select a category when chatting, which filters available tools and documents.

### Creating a Category

1. Navigate to **Categories** in the admin menu
2. Click **+ New Category**
3. Fill in the form:
   - **Name**: Unique identifier (e.g., "engineering", "hr", "finance")
   - **Description**: Human-readable description
   - **Status**: Active or Draft
4. Click **Save**

### Editing a Category

1. Find the category in the list
2. Click the **Edit** (pencil) icon
3. Modify the fields
4. Click **Save Changes**

### Assigning Tools to Categories

1. Edit the category
2. In the "Enabled Tools" section, check the tools to include
3. Click **Save Changes**

### Assigning Documents to Categories

Documents are assigned to categories during upload:

1. Go to **Documents** > **Upload**
2. Select the category from the dropdown
3. Upload the document

### Deleting a Category

**Warning**: Deleting a category does not delete associated documents, but they will become uncategorized.

1. Find the category in the list
2. Click the **Delete** (trash) icon
3. Confirm the deletion

### Best Practices

- Use descriptive, lowercase names (e.g., "technical-support", "sales")
- Keep categories focused - avoid catch-all categories
- Regularly review which tools are enabled per category
- Use Draft status for categories under construction

---

## 4. Tool Management

### What are Tools?

Tools are capabilities the AI can invoke to perform actions:

| Tool Type | Examples |
|-----------|----------|
| Utility | add, echo, get_current_time |
| API | jsonplaceholder-user, weather-api |
| RAG | rag_query, rag_ingest |
| Custom | Your registered external services |

### Viewing Registered Tools

Navigate to **Tools** to see all registered tools with:

- Tool name and endpoint
- Assigned categories
- Health status
- Usage statistics

### Registering Tools from OpenAPI

The easiest way to register external tools:

1. Navigate to **Tools** > **+ Register Tool**
2. Select **OpenAPI Specification URL**
3. Enter the OpenAPI URL (e.g., `http://external-service/v3/api-docs`)
4. Click **Fetch & Parse**
5. Review discovered endpoints
6. Check the endpoints you want to register
7. Select categories to assign
8. Click **Register Selected**

### Manual Tool Registration

For tools without OpenAPI specs:

1. Navigate to **Tools** > **+ Register Tool**
2. Select **Manual Configuration**
3. Fill in:
   - **Name**: Unique tool identifier
   - **Description**: What the tool does
   - **Endpoint**: HTTP endpoint URL
   - **Method**: GET, POST, PUT, DELETE
   - **Input Schema**: JSON Schema for parameters
4. Assign to categories
5. Click **Save**

### Tool Input Schema Format

```json
{
  "type": "object",
  "properties": {
    "paramName": {
      "type": "string",
      "description": "Parameter description"
    },
    "numericParam": {
      "type": "integer",
      "description": "A number parameter",
      "minimum": 1,
      "maximum": 100
    }
  },
  "required": ["paramName"]
}
```

### Editing Tools

1. Find the tool in the list
2. Click the **Edit** (pencil) icon
3. Modify fields
4. Click **Save Changes**

### Deleting Tools

1. Find the tool in the list
2. Click the **Delete** (trash) icon
3. Confirm deletion

**Note**: Deleting a tool removes it from all categories.

### Tool Health Monitoring

- Tools show UP/DOWN status based on health checks
- The "Calls" column shows usage statistics
- Click on a tool to view detailed metrics

---

## 5. Document Management

### Document Storage Architecture

Documents are stored in Qdrant vector database:

1. **Chunking**: Documents are split into ~500 token chunks
2. **Embedding**: Each chunk is converted to a vector
3. **Storage**: Vectors stored with metadata in Qdrant
4. **Retrieval**: RAG queries find relevant chunks

### Uploading Documents (Quick Ingest)

For immediate ingestion without preview:

1. Navigate to **Documents** > **Quick Ingest**
2. Fill in:
   - **Document ID**: Unique identifier (e.g., "product-manual-v1")
   - **Title**: Human-readable title
   - **Category**: (Optional) Category assignment
   - **Content**: Paste document text
3. Click **Ingest**

### Viewing Documents

The documents list shows:

| Column | Description |
|--------|-------------|
| Document ID | Unique identifier |
| Title | Document title |
| Category | Assigned category |
| Chunks | Number of chunks created |
| Q&A | Generated/Validated Q&A count |
| Status | Processing status |
| Created | Upload timestamp |

### Deleting Documents

1. Find the document in the list
2. Click the **Delete** (trash) icon
3. Confirm deletion

**Warning**: This removes all chunks from the vector database.

### RAG Statistics

The Documents page shows RAG statistics:

- **Documents in RAG**: Total document count
- **Total Chunks**: Total chunk count
- **Vector Count**: Vectors in Qdrant

---

## 6. Enhanced Document Upload with Q&A Preview

### Overview

The enhanced upload process validates document quality before adding to RAG:

1. Upload document
2. AI generates Q&A pairs
3. System tests retrieval quality
4. Admin reviews and approves

### Starting an Enhanced Upload

1. Navigate to **Documents** > **Upload with Preview**
2. Fill in:
   - **Document ID**: Unique identifier
   - **Title**: Document title
   - **Category**: (Optional) Category
   - **Content**: Document text
3. Click **Upload & Process**

### Processing Stages

| Stage | Status | Description |
|-------|--------|-------------|
| 1 | PENDING | Upload received |
| 2 | GENERATING_QA | AI creating Q&A pairs |
| 3 | CHUNKING_TEMP | Creating temporary chunks |
| 4 | VALIDATING_QA | Testing RAG retrieval |
| 5 | READY_FOR_REVIEW | Ready for admin review |

### Real-Time Progress

- Progress bar shows processing status
- SSE events update the UI in real-time
- You can leave the page and return later

### Reviewing the Upload

When processing completes, you'll see:

**Document Information**:
- Document ID, title, category
- Number of chunks created
- Creation timestamp

**Validation Statistics**:
- Passed: Q&A pairs that retrieved correct answers
- Failed: Q&A pairs with poor retrieval
- Average Score: Overall similarity score

**Q&A Pairs Table**:

| Column | Description |
|--------|-------------|
| Type | Fine-grain (detailed) or Summary (conceptual) |
| Question | Generated question |
| Expected Answer | AI-generated expected answer |
| RAG Answer | Actual answer from temp collection |
| Score | Similarity score (0-100%) |
| Status | PASSED (>70%) or FAILED |

### Approval Decisions

| Action | When to Use |
|--------|-------------|
| **Approve & Add to RAG** | Good validation scores, content is ready |
| **Retry Processing** | Issues during processing, want to regenerate Q&A |
| **Delete** | Document not suitable, content errors |

### Approving a Document

1. Review the Q&A pairs and scores
2. Click **Approve & Add to RAG**
3. Confirm the action
4. Document moves to main Qdrant collection

### Retrying Processing

1. Click **Retry Processing**
2. System regenerates Q&A pairs
3. Re-validates against temp collection
4. Review again when complete

### Understanding Similarity Scores

| Score | Quality | Recommendation |
|-------|---------|----------------|
| 90-100% | Excellent | Approve |
| 70-89% | Good | Approve |
| 50-69% | Fair | Review content, consider retry |
| Below 50% | Poor | Check document quality, retry or delete |

### Best Practices

- Review all Q&A pairs before approving
- Pay attention to failed Q&A pairs
- Use descriptive document IDs
- Keep documents focused on single topics
- Split large documents into smaller pieces

---

## 7. Audit Logs

### Accessing Audit Logs

Navigate to **Audit Logs** from the admin menu.

### Filtering Logs

| Filter | Options |
|--------|---------|
| Date Range | Start and end date |
| User | All users or specific user |
| Type | CHAT, TOOL_CALL, RAG_QUERY, ADMIN |
| Status | SUCCESS, FAILED, PARTIAL, TIMEOUT |
| Service | Filter by NAAG service |

### Understanding Log Entries

Each log entry shows:

| Field | Description |
|-------|-------------|
| Time | When the action occurred |
| User | User who performed the action |
| Service | Which NAAG service |
| Type | Action type |
| Question | User's input |
| Status | Success or failure |

### Viewing Log Details

Click on any log entry to see full details:

- Complete question and answer
- Tool parameters and results (for tool calls)
- RAG sources (for RAG queries)
- Error messages (for failures)
- Duration and confidence scores

### Exporting Logs

1. Apply desired filters
2. Click **Export CSV** or **Export JSON**
3. Download file contains filtered results

### Log Retention

- Development (H2): Logs stored in local database
- Production (Elasticsearch): Configure retention in settings

---

## 8. Cache Management

### Understanding Caches

NAAG uses caching to improve performance:

| Cache | TTL | Purpose |
|-------|-----|---------|
| tools | 60s | Tool definitions |
| categories | 60s | Category configurations |
| categoryTools | 60s | Tool-to-category mappings |
| ragQueryResults | 300s | RAG query results |
| serviceHealth | 30s | Service health status |
| userSessions | 30min | Active user sessions |

### Viewing Cache Statistics

Navigate to **Cache** to see:

- Total cache entries
- Hit rate percentage
- Memory usage
- Per-cache statistics

### Clearing Caches

**Clear Single Cache**:
1. Find the cache in the list
2. Click the **Clear** (trash) icon
3. Confirm the action

**Clear All Caches**:
1. Click **Clear All Caches**
2. Confirm the action

### When to Clear Caches

| Scenario | Caches to Clear |
|----------|-----------------|
| Tool definitions changed | tools |
| Categories modified | categories, categoryTools |
| Stale RAG results | ragQueryResults |
| Service connectivity issues | serviceHealth |
| User session problems | userSessions |
| After deployments | All caches |

---

## 9. Service Health Monitoring

### Service Status Dashboard

The dashboard shows all NAAG services:

| Service | Port | Function |
|---------|------|----------|
| naagi-rag-service | 8080 | RAG operations |
| naagi-tool-registry | 8081 | Tool metadata |
| naagi-mcp-gateway | 8082 | MCP protocol |
| naagi-utility-tools | 8083 | Built-in tools |
| naagi-category-admin | 8085 | Admin UI |
| naagi-ai-orchestrator | 8086 | LLM routing |
| naagi-chat-app | 8087 | Chat UI |

### External Dependencies

Monitor external services:

| Dependency | Port | Status Check |
|------------|------|--------------|
| Ollama LLM | 11434 | Model availability |
| Qdrant | 6333 | Collection status |
| Elasticsearch | 9200 | Index availability (if enabled) |

### Service Metrics

Click on any service to view:

- Requests per minute
- Average response time
- Error rate
- Cache hit rate
- Memory usage

### Troubleshooting Service Issues

| Symptom | Possible Cause | Solution |
|---------|---------------|----------|
| Service DOWN | Service crashed | Restart the service |
| High response time | Overloaded | Check logs, scale if needed |
| High error rate | Configuration issue | Check service logs |
| Cache miss rate high | TTL too short | Adjust cache settings |

---

## 10. System Settings

### Accessing Settings

Click the **Settings** (gear) icon in the admin menu.

### General Settings

| Setting | Description |
|---------|-------------|
| Platform Name | Display name for the platform |
| Default Category | Category used when none selected |
| Session Timeout | User session duration |

### Cache Settings

| Setting | Description | Default |
|---------|-------------|---------|
| Cache Enabled | Enable/disable caching | true |
| Default TTL | Default cache duration | 60s |
| Tools TTL | Tool cache duration | 60s |
| Categories TTL | Category cache duration | 60s |
| RAG Query TTL | RAG result cache duration | 300s |

### Audit Settings

| Setting | Description |
|---------|-------------|
| Audit Enabled | Enable/disable audit logging |
| Storage Backend | H2, Elasticsearch, or both |
| Retention Days | How long to keep logs |
| Log User Questions | Include user input in logs |
| Log Tool Parameters | Include tool params in logs |

### LLM Configuration

| Setting | Description |
|---------|-------------|
| Provider | Ollama (default) |
| Model | llama3.1 |
| Base URL | http://localhost:11434 |
| Temperature | 0.2 (for tool selection) |
| Max Tokens | 512 |
| Timeout | 30 seconds |

### Confidence Thresholds

| Setting | Description | Default |
|---------|-------------|---------|
| High Threshold | Auto-execute threshold | 0.8 |
| Low Threshold | Ask user threshold | 0.5 |

---

## 11. Troubleshooting

### Common Issues

#### Document Upload Fails

**Symptoms**: Upload stuck at FAILED status

**Possible Causes**:
- Ollama not running
- Qdrant not accessible
- Document too large

**Solutions**:
1. Check Ollama status: `curl http://localhost:11434/api/tags`
2. Check Qdrant status: `curl http://localhost:6333/collections`
3. Split large documents into smaller pieces

#### Q&A Validation Scores Low

**Symptoms**: Many Q&A pairs failing validation

**Possible Causes**:
- Document content unclear
- Chunking not optimal
- Embedding model issues

**Solutions**:
1. Review document for clarity
2. Ensure content is well-structured
3. Try smaller documents
4. Check Ollama embedding model

#### Tools Not Showing

**Symptoms**: Tools registered but not appearing in chat

**Possible Causes**:
- Tool not assigned to category
- Cache not refreshed
- Tool health check failing

**Solutions**:
1. Verify tool assigned to user's category
2. Clear tools cache
3. Check tool endpoint health

#### Service Not Responding

**Symptoms**: Service shows DOWN in dashboard

**Possible Causes**:
- Service crashed
- Port conflict
- Out of memory

**Solutions**:
1. Check service logs
2. Restart the service
3. Verify port availability
4. Check system resources

### Log Locations

| Service | Log Location |
|---------|--------------|
| Spring Boot services | Console output / logs directory |
| Ollama | `~/.ollama/logs/` |
| Qdrant | Docker logs or installation logs |

### Getting Help

1. Check service logs for error messages
2. Review audit logs for failed operations
3. Verify all services are running
4. Check external dependency status
5. Report issues at: https://github.com/anthropics/claude-code/issues

---

## Appendix: Quick Reference

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save form |
| Esc | Cancel/Close dialog |
| Enter | Confirm action |

### Status Badge Colors

| Color | Meaning |
|-------|---------|
| Green | Active/Success/UP |
| Yellow | Warning/Pending/Draft |
| Red | Error/Failed/DOWN |
| Blue | Info/Processing |
| Gray | Inactive/Unknown |

### API Endpoints (Admin)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| /api/categories | GET, POST | List/Create categories |
| /api/categories/{id} | GET, PUT, DELETE | Single category operations |
| /api/tools | GET, POST | List/Register tools |
| /api/tools/{id} | GET, PUT, DELETE | Single tool operations |
| /api/documents/upload | POST | Upload with preview |
| /api/documents/uploads | GET | List uploads |
| /api/audit/logs | GET | Query audit logs |
| /api/admin/cache/stats | GET | Cache statistics |
| /api/admin/cache/clear-all | POST | Clear all caches |
