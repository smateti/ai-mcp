package com.naagi.mcpgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.mcpgateway.model.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class ToolExecutionService {

    private final ToolRegistryClient toolRegistryClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ToolExecutionService(ToolRegistryClient toolRegistryClient, ObjectMapper objectMapper) {
        this.toolRegistryClient = toolRegistryClient;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Object executeTool(String toolName, JsonNode arguments) throws Exception {
        log.info("Executing tool: {} with arguments: {}", toolName, arguments);

        // Get tool definition from registry to know the endpoint
        Tool tool = toolRegistryClient.getToolByName(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        // For now, we need to fetch full tool details including baseUrl and path
        // This would typically come from the tool registry
        JsonNode toolDetails = fetchToolDetails(toolName);
        if (toolDetails == null) {
            throw new IllegalArgumentException("Could not fetch tool details for: " + toolName);
        }

        String baseUrl = toolDetails.has("baseUrl") ? toolDetails.get("baseUrl").asText() : null;
        String path = toolDetails.has("path") ? toolDetails.get("path").asText() : "";
        String httpMethod = toolDetails.has("httpMethod") ? toolDetails.get("httpMethod").asText() : "POST";

        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("Tool has no baseUrl configured: " + toolName);
        }

        // Substitute path parameters like {id} with actual values from arguments
        String resolvedPath = substitutePath(path, arguments);
        String fullUrl = baseUrl + resolvedPath;
        log.info("Calling tool endpoint: {} {}", httpMethod, fullUrl);

        // Build and execute the HTTP request
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");

        HttpRequest request;
        if ("GET".equalsIgnoreCase(httpMethod)) {
            // For GET, append parameters as query string
            String queryString = buildQueryString(arguments);
            if (!queryString.isEmpty()) {
                fullUrl = fullUrl + "?" + queryString;
            }
            request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
        } else {
            // For POST/PUT, send arguments as body
            request = requestBuilder
                    .method(httpMethod, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(arguments)))
                    .build();
        }

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            // Try to parse as JSON, otherwise return as string
            try {
                return objectMapper.readTree(response.body());
            } catch (Exception e) {
                return Map.of("result", response.body());
            }
        } else {
            throw new RuntimeException("Tool execution failed: HTTP " + response.statusCode() + " - " + response.body());
        }
    }

    private JsonNode fetchToolDetails(String toolName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/api/tools/by-tool-id/" + toolName))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
        } catch (Exception e) {
            log.error("Error fetching tool details", e);
        }
        return null;
    }

    private String buildQueryString(JsonNode arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        arguments.fields().forEachRemaining(entry -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue().asText());
        });
        return sb.toString();
    }

    /**
     * Substitute path parameters like {id}, {userId} with actual values from arguments.
     * For example: /users/{id} with {"id": 6} becomes /users/6
     * Values are URL-encoded to handle special characters.
     */
    private String substitutePath(String path, JsonNode arguments) {
        if (path == null || arguments == null) {
            return path;
        }

        String result = path;
        var fields = arguments.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String placeholder = "{" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                String value = entry.getValue().asText();
                String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
                result = result.replace(placeholder, encodedValue);
                log.debug("Substituted {} with {} in path", placeholder, encodedValue);
            }
        }
        return result;
    }
}
