package com.example.mcp.service;

import com.example.mcp.model.*;
import com.example.mcp.protocol.JsonRpcRequest;
import com.example.mcp.protocol.JsonRpcResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class McpProtocolHandler {
    private static final Logger logger = LoggerFactory.getLogger(McpProtocolHandler.class);
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final ResourceRegistry resourceRegistry;

    public McpProtocolHandler(ObjectMapper objectMapper,
                             ToolRegistry toolRegistry,
                             ResourceRegistry resourceRegistry) {
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
    }

    public JsonRpcResponse handleRequest(JsonRpcRequest request) {
        try {
            logger.info("Handling MCP request: {}", request.getMethod());

            return switch (request.getMethod()) {
                case "initialize" -> handleInitialize(request);
                case "tools/list" -> handleToolsList(request);
                case "tools/call" -> handleToolsCall(request);
                case "resources/list" -> handleResourcesList(request);
                case "resources/read" -> handleResourcesRead(request);
                default -> JsonRpcResponse.error(request.getId(), -32601, "Method not found");
            };
        } catch (Exception e) {
            logger.error("Error handling request", e);
            return JsonRpcResponse.error(request.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("serverInfo", new ServerInfo("mcp-spring-boot-server", "1.0.0"));

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("resources", Map.of("subscribe", false, "listChanged", false));
        result.put("capabilities", capabilities);

        return JsonRpcResponse.success(request.getId(), result);
    }

    private JsonRpcResponse handleToolsList(JsonRpcRequest request) {
        List<Tool> tools = toolRegistry.getAllTools();
        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return JsonRpcResponse.success(request.getId(), result);
    }

    private JsonRpcResponse handleToolsCall(JsonRpcRequest request) throws Exception {
        JsonNode params = request.getParams();
        String toolName = params.get("name").asText();
        JsonNode arguments = params.get("arguments");

        Object toolResult = toolRegistry.executeTool(toolName, arguments);

        Map<String, Object> result = new HashMap<>();
        result.put("content", List.of(new TextContent(objectMapper.writeValueAsString(toolResult))));

        return JsonRpcResponse.success(request.getId(), result);
    }

    private JsonRpcResponse handleResourcesList(JsonRpcRequest request) {
        List<Resource> resources = resourceRegistry.getAllResources();
        Map<String, Object> result = new HashMap<>();
        result.put("resources", resources);
        return JsonRpcResponse.success(request.getId(), result);
    }

    private JsonRpcResponse handleResourcesRead(JsonRpcRequest request) throws Exception {
        JsonNode params = request.getParams();
        String uri = params.get("uri").asText();

        String content = resourceRegistry.readResource(uri);

        Map<String, Object> result = new HashMap<>();
        result.put("contents", List.of(Map.of(
            "uri", uri,
            "mimeType", "text/plain",
            "text", content
        )));

        return JsonRpcResponse.success(request.getId(), result);
    }
}
