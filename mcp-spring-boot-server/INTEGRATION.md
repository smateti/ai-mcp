# MCP Server + Dynamic Tool Registry Integration

This document explains how the MCP Spring Boot Server integrates with the Dynamic Tool Registry.

## Overview

The MCP server now dynamically loads tools from the Dynamic Tool Registry at startup, allowing you to manage and register tools via a web interface instead of hardcoding them.

## Architecture

```
┌─────────────────────────────┐
│  Dynamic Tool Registry      │
│  (Port 8080)                │
│  - Web UI for managing tools│
│  - Parses OpenAPI specs     │
│  - Stores tool definitions  │
└──────────┬──────────────────┘
           │ HTTP REST API
           │ GET /api/tools
           ▼
┌─────────────────────────────┐
│  MCP Spring Boot Server     │
│  - Loads tools at startup   │
│  - Serves tools via MCP     │
│  - Stdio transport          │
└─────────────────────────────┘
```

## How It Works

1. **Startup**: When the MCP server starts, it:
   - Registers default built-in tools (echo, add, get_current_time)
   - Calls the Dynamic Tool Registry API to fetch all registered tools
   - Converts each registry tool definition to MCP tool format
   - Registers them in the MCP ToolRegistry

2. **Tool Registration**: Registry tools are converted:
   - Registry parameters → MCP JSON Schema
   - Nested parameters → Nested object schemas
   - Human-readable descriptions → Tool/parameter descriptions
   - Types are mapped (string, number, boolean, object, array)

3. **Tool Execution**: When a registry tool is called:
   - Arguments are logged
   - Metadata about the tool execution is returned
   - (Future: Could execute actual HTTP calls to the APIs)

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Enable/disable registry integration
tool.registry.enabled=true

# Registry URL (default: http://localhost:8080)
tool.registry.url=http://localhost:8080
```

## Usage Workflow

### Step 1: Start the Dynamic Tool Registry

```bash
cd c:/Users/smate/dynamic-tool-registry
mvn spring-boot:run
```

Access at: http://localhost:8080

### Step 2: Register Tools via Web UI

1. Navigate to http://localhost:8080/tools/new
2. Enter:
   - **Tool ID**: Unique identifier (e.g., `propset-list`)
   - **OpenAPI Endpoint**: URL or file path (e.g., `file:///c:/tmp/a.json`)
   - **API Path**: Endpoint path (e.g., `/api/propset/list/{propSetId}`)
   - **HTTP Method**: GET, POST, PUT, DELETE, or PATCH
3. Click "Preview Tool" to see parsed parameters
4. Add human-readable descriptions for AI models
5. Click "Confirm Registration"

### Step 3: Start MCP Server

The MCP server will automatically load tools from the registry:

```bash
cd c:/Users/smate/mcp-spring-boot-server
mvn spring-boot:run
```

Check logs for:
```
Loading tools from dynamic tool registry...
Registered tool from registry: propset-list (GET__api_propset_list_{propSetId})
Loaded 1 tools from registry
```

### Step 4: Use Tools in MCP

Tools from the registry are now available alongside built-in tools. When a client calls `tools/list`, they'll see:
- Built-in tools: `echo`, `add`, `get_current_time`
- Registry tools: `propset-list`, `propset-save`, etc.

## Example: Registering an OpenAPI Tool

**OpenAPI File** (`c:/tmp/a.json`):
```yaml
paths:
  /api/propset/list/{propSetId}:
    get:
      parameters:
      - name: propSetId
        in: path
        required: true
        schema:
          type: string
```

**Registry Registration**:
- Tool ID: `get-propset`
- OpenAPI Endpoint: `file:///c:/tmp/a.json`
- Path: `/api/propset/list/{propSetId}`
- Method: GET

**Result in MCP**:
```json
{
  "name": "get-propset",
  "description": "Retrieves property set by ID",
  "inputSchema": {
    "type": "object",
    "properties": {
      "propSetId": {
        "type": "string",
        "description": "The unique identifier for the property set"
      }
    },
    "required": ["propSetId"]
  }
}
```

## Features

### Supported OpenAPI Features

✅ Path parameters (`/api/users/{userId}`)
✅ Query parameters (`/api/users?page=1`)
✅ Request body with $ref schemas
✅ Nested object parameters
✅ Array parameters
✅ Human-readable descriptions for AI models

### Type Mapping

| Registry Type | JSON Schema Type |
|--------------|------------------|
| string | string |
| integer, int | integer |
| number, double, float | number |
| boolean, bool | boolean |
| object (ClassName) | object |
| array[Type] | array |

## Troubleshooting

### Tools Not Loading

Check registry is running:
```bash
curl http://localhost:8080/api/tools
```

Should return JSON array of tools.

### Disable Registry Integration

Set in `application.properties`:
```properties
tool.registry.enabled=false
```

### Change Registry URL

Set in `application.properties`:
```properties
tool.registry.url=http://different-host:9090
```

## Future Enhancements

- [ ] Actual HTTP execution of registry tools
- [ ] OAuth/API key support for external APIs
- [ ] Tool refresh without restarting MCP server
- [ ] Tool versioning and updates
- [ ] Response schema validation

## Benefits

1. **No Code Changes**: Add new tools without modifying MCP server code
2. **OpenAPI Integration**: Leverage existing API documentation
3. **AI-Friendly**: Add human-readable descriptions for better AI understanding
4. **Web UI**: Manage tools through intuitive interface
5. **Centralized**: Single source of truth for tool definitions

## Files Modified

- `pom.xml` - Added WebFlux dependency
- `ToolRegistry.java` - Added registry integration
- `ToolRegistryClient.java` - HTTP client for registry API
- `RegistryToolDefinition.java` - DTO for tool definitions
- `RegistryParameterDefinition.java` - DTO for parameters
- `application.properties` - Configuration properties
