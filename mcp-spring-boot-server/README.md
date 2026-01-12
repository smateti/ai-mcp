# MCP Spring Boot Server

A Model Context Protocol (MCP) server implementation using Spring Boot.

## Features

- **MCP Protocol Support**: Full implementation of MCP protocol version 2024-11-05
- **Stdio Transport**: Communication via standard input/output
- **Tools**: Extensible tool system with example tools (echo, add, get_current_time)
- **Resources**: Resource management system with example resources
- **Spring Boot**: Built on Spring Boot for dependency injection and configuration

## Project Structure

```
mcp-spring-boot-server/
├── src/main/java/com/example/mcp/
│   ├── McpServerApplication.java       # Main application entry point
│   ├── config/
│   │   └── JacksonConfig.java          # Jackson ObjectMapper configuration
│   ├── model/
│   │   ├── Resource.java               # Resource model
│   │   ├── ServerInfo.java             # Server information model
│   │   ├── TextContent.java            # Text content model
│   │   └── Tool.java                   # Tool model
│   ├── protocol/
│   │   ├── JsonRpcError.java           # JSON-RPC error model
│   │   ├── JsonRpcRequest.java         # JSON-RPC request model
│   │   └── JsonRpcResponse.java        # JSON-RPC response model
│   ├── service/
│   │   ├── McpProtocolHandler.java     # Main protocol handler
│   │   ├── ResourceRegistry.java       # Resource registration and management
│   │   └── ToolRegistry.java           # Tool registration and execution
│   └── transport/
│       └── StdioTransport.java         # Stdio transport implementation
└── src/main/resources/
    └── application.properties          # Spring Boot configuration
```

## Building

```bash
mvn clean package
```

## Running

```bash
java -jar target/mcp-spring-boot-server-1.0.0.jar
```

## Available Tools

### 1. echo
Echoes back the input message.

**Parameters:**
- `message` (string): The message to echo back

**Example:**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "echo",
    "arguments": {
      "message": "Hello, MCP!"
    }
  },
  "id": 1
}
```

### 2. add
Adds two numbers together.

**Parameters:**
- `a` (number): First number
- `b` (number): Second number

**Example:**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "add",
    "arguments": {
      "a": 5,
      "b": 3
    }
  },
  "id": 2
}
```

### 3. get_current_time
Returns the current server time.

**Example:**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_current_time",
    "arguments": {}
  },
  "id": 3
}
```

## Available Resources

### 1. resource://config/server
Server configuration settings (JSON format)

### 2. resource://docs/welcome
Welcome documentation (plain text)

## Extending the Server

### Adding Custom Tools

Edit `ToolRegistry.java` and add your tool in the `registerDefaultTools()` method:

```java
registerTool(
    "my_tool",
    "Description of my tool",
    createMyToolSchema(),
    this::executeMyTool
);
```

Then implement the executor method:

```java
private Object executeMyTool(JsonNode args) {
    // Your tool implementation
    return Map.of("result", "value");
}
```

### Adding Custom Resources

Edit `ResourceRegistry.java` and add your resource in the `registerDefaultResources()` method:

```java
registerResource(
    "resource://my/resource",
    "My Resource",
    "Description of my resource",
    "text/plain",
    "Resource content here"
);
```

## MCP Protocol Methods

The server implements the following MCP methods:

- `initialize` - Initialize the MCP connection
- `tools/list` - List available tools
- `tools/call` - Execute a tool
- `resources/list` - List available resources
- `resources/read` - Read a resource

## Requirements

- Java 17 or higher
- Maven 3.6+

## License

MIT License
