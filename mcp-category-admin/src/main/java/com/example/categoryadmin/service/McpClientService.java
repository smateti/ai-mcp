package com.example.categoryadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to communicate with MCP Server to fetch available tools
 */
@Service
@Slf4j
public class McpClientService {

    private final String mcpServerUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    public McpClientService(
            @Value("${mcp.server.url:http://localhost:8082}") String mcpServerUrl,
            ObjectMapper objectMapper) {
        this.mcpServerUrl = mcpServerUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        log.info("MCP Client configured to connect to: {}", mcpServerUrl);
    }

    /**
     * List all available tools from MCP server
     */
    public List<ToolInfo> listTools() {
        try {
            ObjectNode request = createJsonRpcRequest("tools/list", null);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(mcpServerUrl + "/mcp/execute"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("MCP server returned status {}: {}", response.statusCode(), response.body());
                return List.of();
            }

            JsonNode responseNode = objectMapper.readTree(response.body());
            JsonNode toolsNode = responseNode.path("result").path("tools");

            List<ToolInfo> tools = new ArrayList<>();
            if (toolsNode.isArray()) {
                for (JsonNode toolNode : toolsNode) {
                    String name = toolNode.path("name").asText();
                    String description = toolNode.path("description").asText();
                    JsonNode inputSchema = toolNode.path("inputSchema");

                    tools.add(new ToolInfo(name, description, inputSchema));
                }
            }

            log.info("Fetched {} tools from MCP server", tools.size());
            return tools;

        } catch (Exception e) {
            log.error("Failed to list tools from MCP server", e);
            return List.of();
        }
    }

    /**
     * Check if MCP server is available
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mcpServerUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("MCP server health check failed: {}", e.getMessage());
            return false;
        }
    }

    private ObjectNode createJsonRpcRequest(String method, ObjectNode params) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }
        return request;
    }

    /**
     * Tool information from MCP server
     */
    public record ToolInfo(
            String name,
            String description,
            JsonNode inputSchema
    ) {}
}
