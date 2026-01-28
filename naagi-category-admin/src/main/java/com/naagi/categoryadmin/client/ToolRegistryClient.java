package com.naagi.categoryadmin.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.categoryadmin.model.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class ToolRegistryClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ToolRegistryClient(
            @Value("${naagi.services.tool-registry.url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<Tool> getAllTools() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to get tools: HTTP {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching tools from registry", e);
            return Collections.emptyList();
        }
    }

    public Optional<Tool> getTool(String toolId) {
        try {
            // Use by-tool-id endpoint for string tool IDs
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/by-tool-id/" + toolId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Optional.of(objectMapper.readValue(response.body(), Tool.class));
            } else if (response.statusCode() == 404) {
                return Optional.empty();
            } else {
                log.error("Failed to get tool {}: HTTP {}", toolId, response.statusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error fetching tool {} from registry", toolId, e);
            return Optional.empty();
        }
    }

    public Tool createTool(Tool tool) {
        try {
            // Create simple tool request matching the API
            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("toolId", tool.getId());
            requestBody.put("name", tool.getName());
            requestBody.put("description", tool.getDescription());
            requestBody.put("baseUrl", tool.getServiceUrl());
            requestBody.put("path", tool.getPath());
            requestBody.put("httpMethod", tool.getMethod());
            requestBody.put("categoryId", tool.getCategoryId());
            if (tool.getInputSchema() != null) {
                requestBody.put("inputSchema", tool.getInputSchema());
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("Creating tool with request: {}", jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("Tool created successfully: {}", tool.getId());
                return objectMapper.readValue(response.body(), Tool.class);
            } else {
                log.error("Failed to create tool: HTTP {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to create tool: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error creating tool in registry: {}", tool.getId(), e);
            throw new RuntimeException("Error creating tool", e);
        }
    }

    public Tool updateTool(String toolId, Tool tool) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/by-tool-id/" + toolId))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(tool)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Tool.class);
            } else {
                log.error("Failed to update tool {}: HTTP {}", toolId, response.statusCode());
                throw new RuntimeException("Failed to update tool: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error updating tool {} in registry", toolId, e);
            throw new RuntimeException("Error updating tool", e);
        }
    }

    public void deleteTool(String toolId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/by-tool-id/" + toolId))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                log.error("Failed to delete tool {}: HTTP {} - {}", toolId, response.statusCode(), response.body());
                throw new RuntimeException("Failed to delete tool: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error deleting tool {} from registry", toolId, e);
            throw new RuntimeException("Error deleting tool", e);
        }
    }

    /**
     * Get full tool details including parameters and responses.
     */
    public Optional<com.fasterxml.jackson.databind.JsonNode> getToolDetails(String toolId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/by-tool-id/" + toolId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Optional.of(objectMapper.readTree(response.body()));
            } else if (response.statusCode() == 404) {
                return Optional.empty();
            } else {
                log.error("Failed to get tool details {}: HTTP {}", toolId, response.statusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error fetching tool details {} from registry", toolId, e);
            return Optional.empty();
        }
    }

    /**
     * Update tool basic details (description, humanReadableDescription, categoryId).
     */
    public void updateToolDetails(String toolId, String description, String humanReadableDescription, String categoryId) {
        try {
            Map<String, String> requestBody = new java.util.HashMap<>();
            if (description != null) requestBody.put("description", description);
            if (humanReadableDescription != null) requestBody.put("humanReadableDescription", humanReadableDescription);
            if (categoryId != null) requestBody.put("categoryId", categoryId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/by-tool-id/" + toolId))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to update tool details {}: HTTP {} - {}", toolId, response.statusCode(), response.body());
                throw new RuntimeException("Failed to update tool details: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error updating tool details {} in registry", toolId, e);
            throw new RuntimeException("Error updating tool details", e);
        }
    }

    /**
     * Update a parameter (description, example, enum values).
     */
    public void updateParameter(String toolId, Long parameterId, String humanReadableDescription, String example, List<String> enumValues) {
        try {
            Map<String, Object> requestBody = new java.util.HashMap<>();
            if (humanReadableDescription != null) requestBody.put("humanReadableDescription", humanReadableDescription);
            if (example != null) requestBody.put("example", example);
            if (enumValues != null) requestBody.put("enumValues", enumValues);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/" + toolId + "/parameters/" + parameterId))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to update parameter {}: HTTP {} - {}", parameterId, response.statusCode(), response.body());
                throw new RuntimeException("Failed to update parameter: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error updating parameter {} in registry", parameterId, e);
            throw new RuntimeException("Error updating parameter", e);
        }
    }

    /**
     * Update a response description.
     */
    public void updateResponse(String toolId, Long responseId, String humanReadableDescription) {
        try {
            Map<String, String> requestBody = new java.util.HashMap<>();
            if (humanReadableDescription != null) requestBody.put("humanReadableDescription", humanReadableDescription);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/" + toolId + "/responses/" + responseId + "/description"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to update response {}: HTTP {} - {}", responseId, response.statusCode(), response.body());
                throw new RuntimeException("Failed to update response: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error updating response {} in registry", responseId, e);
            throw new RuntimeException("Error updating response", e);
        }
    }

    public List<Tool> getToolsByCategory(String categoryId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools?categoryId=" + categoryId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to get tools for category {}: HTTP {}", categoryId, response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching tools for category {} from registry", categoryId, e);
            return Collections.emptyList();
        }
    }

    public JsonNode registerFromOpenApi(String openApiUrl, String categoryId) {
        try {
            Map<String, String> body = Map.of(
                    "openApiUrl", openApiUrl,
                    "categoryId", categoryId != null ? categoryId : ""
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/register-openapi"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Error registering tools from OpenAPI", e);
            throw new RuntimeException("Error registering from OpenAPI", e);
        }
    }

    /**
     * Register tools from OpenAPI content (file upload).
     */
    public JsonNode registerFromOpenApiContent(String openApiContent, String baseUrl, String categoryId, String filename) {
        try {
            Map<String, String> body = new java.util.HashMap<>();
            body.put("openApiContent", openApiContent);
            body.put("baseUrl", baseUrl);
            body.put("categoryId", categoryId != null ? categoryId : "");
            body.put("filename", filename != null ? filename : "openapi.json");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/api/tools/register-openapi-content"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Tool registry returned error: " + response.body());
            }

            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Error registering tools from OpenAPI content", e);
            throw new RuntimeException("Error registering from OpenAPI content: " + e.getMessage(), e);
        }
    }

    /**
     * Get available paths from an OpenAPI specification.
     */
    public JsonNode getOpenApiPaths(String openApiUrl) {
        try {
            String encodedUrl = java.net.URLEncoder.encode(openApiUrl, java.nio.charset.StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/openapi/paths?openApiUrl=" + encodedUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Error fetching OpenAPI paths", e);
            throw new RuntimeException("Error fetching OpenAPI paths: " + e.getMessage(), e);
        }
    }

    /**
     * Parse OpenAPI content and return available paths.
     */
    public JsonNode parseOpenApiContent(String content) {
        try {
            Map<String, String> body = Map.of("content", content);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/openapi/parse-content"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Error parsing OpenAPI content", e);
            throw new RuntimeException("Error parsing OpenAPI content: " + e.getMessage(), e);
        }
    }

    /**
     * Preview a tool from OpenAPI specification before registering.
     */
    public JsonNode previewTool(String toolId, String openApiEndpoint, String path, String httpMethod) {
        try {
            Map<String, String> body = new java.util.HashMap<>();
            body.put("toolId", toolId);
            body.put("openApiEndpoint", openApiEndpoint);
            body.put("path", path);
            body.put("httpMethod", httpMethod);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/preview"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Preview failed: " + response.body());
            }

            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Error previewing tool", e);
            throw new RuntimeException("Error previewing tool: " + e.getMessage(), e);
        }
    }

    /**
     * Preview a tool from OpenAPI content (file upload).
     */
    public JsonNode previewToolFromContent(String toolId, String openApiContent, String path, String httpMethod) {
        try {
            Map<String, String> body = new java.util.HashMap<>();
            body.put("toolId", toolId);
            body.put("openApiContent", openApiContent);
            body.put("path", path);
            body.put("httpMethod", httpMethod);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tools/preview-content"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Preview failed: " + response.body());
            }

            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Error previewing tool from content", e);
            throw new RuntimeException("Error previewing tool from content: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a tool with the same path and method already exists.
     */
    public boolean checkDuplicate(String openApiEndpoint, String path, String httpMethod) {
        try {
            String url = String.format("%s/api/tools/check-duplicate?openApiEndpoint=%s&path=%s&httpMethod=%s",
                    baseUrl,
                    java.net.URLEncoder.encode(openApiEndpoint, java.nio.charset.StandardCharsets.UTF_8),
                    java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8),
                    java.net.URLEncoder.encode(httpMethod, java.nio.charset.StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode result = objectMapper.readTree(response.body());
                return result.has("exists") && result.get("exists").asBoolean();
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking duplicate", e);
            return false;
        }
    }

    /**
     * Register a single tool from OpenAPI URL with all parameter annotations.
     */
    public JsonNode registerSingleTool(Map<String, Object> registrationRequest) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/api/tools/register"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(registrationRequest)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String errorMsg = extractErrorMessage(response.body());
                throw new RuntimeException(errorMsg);
            }

            return objectMapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error registering single tool", e);
            throw new RuntimeException("Error registering tool: " + e.getMessage(), e);
        }
    }

    /**
     * Register a single tool from OpenAPI content (file upload).
     */
    public JsonNode registerSingleToolFromContent(Map<String, Object> registrationRequest) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.baseUrl + "/api/tools/register-content"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(registrationRequest)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String errorMsg = extractErrorMessage(response.body());
                throw new RuntimeException(errorMsg);
            }

            return objectMapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error registering tool from content", e);
            throw new RuntimeException("Error registering tool: " + e.getMessage(), e);
        }
    }

    /**
     * Extract error message from JSON response body.
     */
    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("error")) {
                return json.get("error").asText();
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }
}
