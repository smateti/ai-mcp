package com.naag.mcpgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naag.mcpgateway.model.ServerInfo;
import com.naag.mcpgateway.model.TextContent;
import com.naag.mcpgateway.model.Tool;
import com.naag.mcpgateway.protocol.JsonRpcRequest;
import com.naag.mcpgateway.protocol.JsonRpcResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class McpProtocolHandler {

    private final ObjectMapper objectMapper;
    private final ToolRegistryClient toolRegistryClient;
    private final ToolExecutionService toolExecutionService;

    public McpProtocolHandler(ObjectMapper objectMapper,
                              ToolRegistryClient toolRegistryClient,
                              ToolExecutionService toolExecutionService) {
        this.objectMapper = objectMapper;
        this.toolRegistryClient = toolRegistryClient;
        this.toolExecutionService = toolExecutionService;
    }

    public JsonRpcResponse handleRequest(JsonRpcRequest request) {
        try {
            log.info("Handling MCP request: {}", request.getMethod());

            return switch (request.getMethod()) {
                case "initialize" -> handleInitialize(request);
                case "tools/list" -> handleToolsList(request);
                case "tools/call" -> handleToolsCall(request);
                default -> JsonRpcResponse.error(request.getId(), -32601, "Method not found: " + request.getMethod());
            };
        } catch (Exception e) {
            log.error("Error handling request", e);
            return JsonRpcResponse.error(request.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("serverInfo", new ServerInfo("naag-mcp-gateway", "1.0.0"));

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        result.put("capabilities", capabilities);

        return JsonRpcResponse.success(request.getId(), result);
    }

    private JsonRpcResponse handleToolsList(JsonRpcRequest request) {
        List<Tool> tools = toolRegistryClient.getAllTools();
        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return JsonRpcResponse.success(request.getId(), result);
    }

    private JsonRpcResponse handleToolsCall(JsonRpcRequest request) {
        try {
            JsonNode params = request.getParams();
            String toolName = params.get("name").asText();
            JsonNode arguments = params.get("arguments");

            Object toolResult = toolExecutionService.executeTool(toolName, arguments);

            Map<String, Object> result = new HashMap<>();
            result.put("content", List.of(new TextContent(objectMapper.writeValueAsString(toolResult))));

            return JsonRpcResponse.success(request.getId(), result);
        } catch (Exception e) {
            log.error("Error executing tool", e);
            return JsonRpcResponse.error(request.getId(), -32603, "Tool execution failed: " + e.getMessage());
        }
    }
}
