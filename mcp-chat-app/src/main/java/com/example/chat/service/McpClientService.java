package com.example.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to communicate with MCP Server via JSON-RPC 2.0 protocol
 */
@Service
@Slf4j
public class McpClientService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    public McpClientService(
            @Value("${mcp.server.url}") String mcpServerUrl,
            @Value("${mcp.server.execute-endpoint}") String executeEndpoint,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(mcpServerUrl + executeEndpoint)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * List all available tools from MCP server
     */
    public List<Tool> listTools() {
        try {
            ObjectNode request = createJsonRpcRequest("tools/list", null);

            String response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);
            JsonNode toolsNode = responseNode.path("result").path("tools");

            return objectMapper.convertValue(
                    toolsNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Tool.class)
            );
        } catch (Exception e) {
            log.error("Failed to list tools from MCP server", e);
            throw new RuntimeException("Failed to list tools", e);
        }
    }

    /**
     * Execute a tool via MCP server
     */
    public ToolExecutionResult executeTool(String toolName, Map<String, Object> arguments) {
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", objectMapper.valueToTree(arguments));

            ObjectNode request = createJsonRpcRequest("tools/call", params);

            log.debug("Executing tool: {} with arguments: {}", toolName, arguments);

            String response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);

            // Check for JSON-RPC error
            if (responseNode.has("error")) {
                JsonNode error = responseNode.get("error");
                String errorMessage = error.path("message").asText();
                log.error("Tool execution failed: {}", errorMessage);
                return ToolExecutionResult.error(toolName, errorMessage);
            }

            // Extract result
            JsonNode result = responseNode.path("result");

            return ToolExecutionResult.success(toolName, result);

        } catch (Exception e) {
            log.error("Failed to execute tool: {}", toolName, e);
            return ToolExecutionResult.error(toolName, e.getMessage());
        }
    }

    /**
     * Ingest document into RAG system
     */
    public ToolExecutionResult ingestDocument(String docId, String text) {
        Map<String, Object> args = Map.of(
                "docId", docId,
                "text", text
        );
        return executeTool("rag_ingest", args);
    }

    /**
     * Query RAG system
     */
    public ToolExecutionResult queryRag(String question, int topK) {
        Map<String, Object> args = Map.of(
                "question", question,
                "topK", topK
        );
        return executeTool("rag_query", args);
    }

    /**
     * Query RAG system with default topK
     */
    public ToolExecutionResult queryRag(String question) {
        return queryRag(question, 5);
    }

    private ObjectNode createJsonRpcRequest(String method, JsonNode params) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }
        request.put("id", requestIdCounter.getAndIncrement());
        return request;
    }

    /**
     * Tool metadata from MCP server
     */
    public record Tool(
            String name,
            String description,
            JsonNode inputSchema
    ) {}

    /**
     * Result of tool execution
     */
    public record ToolExecutionResult(
            String toolName,
            boolean success,
            JsonNode result,
            String errorMessage
    ) {
        public static ToolExecutionResult success(String toolName, JsonNode result) {
            return new ToolExecutionResult(toolName, true, result, null);
        }

        public static ToolExecutionResult error(String toolName, String errorMessage) {
            return new ToolExecutionResult(toolName, false, null, errorMessage);
        }
    }
}
