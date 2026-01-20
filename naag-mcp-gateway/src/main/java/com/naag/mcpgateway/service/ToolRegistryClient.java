package com.naag.mcpgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naag.mcpgateway.model.Tool;
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

@Service
@Slf4j
public class ToolRegistryClient {

    private final String toolRegistryUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ToolRegistryClient(
            @Value("${naag.services.tool-registry.url}") String toolRegistryUrl,
            ObjectMapper objectMapper
    ) {
        this.toolRegistryUrl = toolRegistryUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<Tool> getAllTools() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(toolRegistryUrl + "/api/tools"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch tools from registry: HTTP {}", response.statusCode());
                return List.of();
            }

            JsonNode toolsArray = objectMapper.readTree(response.body());
            List<Tool> tools = new ArrayList<>();

            for (JsonNode toolNode : toolsArray) {
                Tool tool = convertToMcpTool(toolNode);
                if (tool != null) {
                    tools.add(tool);
                }
            }

            log.info("Loaded {} tools from registry", tools.size());
            return tools;
        } catch (Exception e) {
            log.error("Error fetching tools from registry", e);
            return List.of();
        }
    }

    public Tool getToolByName(String toolName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(toolRegistryUrl + "/api/tools/by-tool-id/" + toolName))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Tool not found in registry: {}", toolName);
                return null;
            }

            JsonNode toolNode = objectMapper.readTree(response.body());
            return convertToMcpTool(toolNode);
        } catch (Exception e) {
            log.error("Error fetching tool {} from registry", toolName, e);
            return null;
        }
    }

    private Tool convertToMcpTool(JsonNode toolNode) {
        try {
            String name = toolNode.get("toolId").asText();
            String description = toolNode.has("humanReadableDescription") && !toolNode.get("humanReadableDescription").isNull()
                    ? toolNode.get("humanReadableDescription").asText()
                    : toolNode.get("description").asText();

            // Build input schema from parameters
            JsonNode inputSchema = buildInputSchema(toolNode.get("parameters"));

            return new Tool(name, description, inputSchema);
        } catch (Exception e) {
            log.error("Error converting tool from registry", e);
            return null;
        }
    }

    private JsonNode buildInputSchema(JsonNode parameters) {
        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        var properties = objectMapper.createObjectNode();
        var required = objectMapper.createArrayNode();

        if (parameters != null && parameters.isArray()) {
            for (JsonNode param : parameters) {
                String paramName = param.get("name").asText();
                String paramType = param.has("type") ? param.get("type").asText() : "string";
                boolean isRequired = param.has("required") && param.get("required").asBoolean();

                var paramSchema = objectMapper.createObjectNode();
                paramSchema.put("type", mapTypeToJsonSchemaType(paramType));

                if (param.has("description") && !param.get("description").isNull()) {
                    paramSchema.put("description", param.get("description").asText());
                }

                properties.set(paramName, paramSchema);

                if (isRequired) {
                    required.add(paramName);
                }
            }
        }

        schema.set("properties", properties);
        schema.set("required", required);

        return schema;
    }

    private String mapTypeToJsonSchemaType(String type) {
        if (type == null) return "string";

        return switch (type.toLowerCase()) {
            case "integer", "int", "long" -> "integer";
            case "number", "double", "float" -> "number";
            case "boolean", "bool" -> "boolean";
            case "array" -> "array";
            case "object" -> "object";
            default -> "string";
        };
    }
}
