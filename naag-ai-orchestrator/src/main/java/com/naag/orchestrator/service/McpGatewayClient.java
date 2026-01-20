package com.naag.orchestrator.service;

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
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class McpGatewayClient {

    private final String mcpGatewayUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public McpGatewayClient(
            @Value("${naag.services.mcp-gateway.url}") String mcpGatewayUrl,
            ObjectMapper objectMapper) {
        this.mcpGatewayUrl = mcpGatewayUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public JsonNode executeTool(String toolName, Map<String, Object> parameters) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", "tools/call");
            request.put("id", UUID.randomUUID().toString());

            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", objectMapper.valueToTree(parameters));
            request.set("params", params);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(mcpGatewayUrl + "/mcp/execute"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Tool execution failed: HTTP {}", response.statusCode());
                return null;
            }

            JsonNode result = objectMapper.readTree(response.body());

            if (result.has("result")) {
                return result.get("result");
            } else if (result.has("error")) {
                log.error("Tool execution error: {}", result.get("error"));
                return null;
            }

            return result;
        } catch (Exception e) {
            log.error("Error executing tool {} via MCP gateway", toolName, e);
            return null;
        }
    }
}
