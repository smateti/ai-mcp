package com.naag.toolregistry.controller;

import com.naag.toolregistry.dto.ParameterUpdateRequest;
import com.naag.toolregistry.dto.ParsedToolInfo;
import com.naag.toolregistry.dto.ResponseUpdateRequest;
import com.naag.toolregistry.dto.ToolRegistrationRequest;
import com.naag.toolregistry.entity.ToolDefinition;
import com.naag.toolregistry.service.ToolRegistrationService;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ToolApiController {

    private final ToolRegistrationService toolRegistrationService;

    @PostMapping("/register")
    public ResponseEntity<?> registerTool(@RequestBody ToolRegistrationRequest request) {
        try {
            ToolDefinition tool = toolRegistrationService.registerTool(request);
            return ResponseEntity.ok(tool);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    /**
     * Register a tool from OpenAPI content (file upload).
     * Used when the OpenAPI spec is provided as content rather than a URL.
     */
    @PostMapping("/register-content")
    public ResponseEntity<?> registerToolFromContent(@RequestBody Map<String, Object> request) {
        try {
            String toolId = (String) request.get("toolId");
            String openApiContent = (String) request.get("openApiContent");
            String path = (String) request.get("path");
            String httpMethod = (String) request.get("httpMethod");
            String baseUrl = (String) request.get("baseUrl");
            String humanReadableDescription = (String) request.get("humanReadableDescription");
            String categoryId = (String) request.get("categoryId");

            if (toolId == null || toolId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tool ID is required"));
            }
            if (openApiContent == null || openApiContent.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "OpenAPI content is required"));
            }

            // Build the registration request
            ToolRegistrationRequest toolRequest = new ToolRegistrationRequest();
            toolRequest.setToolId(toolId);
            toolRequest.setOpenApiEndpoint("content:" + toolId);
            toolRequest.setPath(path);
            toolRequest.setHttpMethod(httpMethod);
            toolRequest.setBaseUrl(baseUrl);
            toolRequest.setHumanReadableDescription(humanReadableDescription);
            toolRequest.setCategoryId(categoryId);

            // Extract parameter annotations if provided
            if (request.get("paramNames") != null) {
                @SuppressWarnings("unchecked")
                java.util.List<String> paramNamesList = (java.util.List<String>) request.get("paramNames");
                toolRequest.setParamNames(paramNamesList.toArray(new String[0]));
            }
            if (request.get("paramNestingLevels") != null) {
                @SuppressWarnings("unchecked")
                java.util.List<Integer> levels = (java.util.List<Integer>) request.get("paramNestingLevels");
                toolRequest.setParamNestingLevels(levels.toArray(new Integer[0]));
            }
            if (request.get("paramHumanDescriptions") != null) {
                @SuppressWarnings("unchecked")
                java.util.List<String> descs = (java.util.List<String>) request.get("paramHumanDescriptions");
                toolRequest.setParamHumanDescriptions(descs.toArray(new String[0]));
            }
            if (request.get("paramExamples") != null) {
                @SuppressWarnings("unchecked")
                java.util.List<String> examples = (java.util.List<String>) request.get("paramExamples");
                toolRequest.setParamExamples(examples.toArray(new String[0]));
            }

            ToolDefinition tool = toolRegistrationService.registerToolFromContent(toolRequest, openApiContent);
            return ResponseEntity.ok(tool);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    /**
     * Simple tool creation endpoint - creates a tool without OpenAPI parsing.
     * Useful for registering public APIs manually.
     */
    @PostMapping
    public ResponseEntity<ToolDefinition> createSimpleTool(@RequestBody SimpleToolRequest request) {
        try {
            ToolDefinition tool = toolRegistrationService.createSimpleTool(request);
            return ResponseEntity.ok(tool);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Simple tool creation request DTO.
     */
    public record SimpleToolRequest(
            String toolId,
            String name,
            String description,
            String baseUrl,
            String path,
            String httpMethod,
            String categoryId,
            Map<String, Object> inputSchema
    ) {}

    @PostMapping("/preview")
    public ResponseEntity<ParsedToolInfo> previewTool(@RequestBody ToolRegistrationRequest request) {
        try {
            ParsedToolInfo preview = toolRegistrationService.previewTool(request);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Preview a tool from OpenAPI content (file upload).
     */
    @PostMapping("/preview-content")
    public ResponseEntity<ParsedToolInfo> previewToolFromContent(@RequestBody Map<String, String> request) {
        try {
            String toolId = request.get("toolId");
            String openApiContent = request.get("openApiContent");
            String path = request.get("path");
            String httpMethod = request.get("httpMethod");

            if (openApiContent == null || openApiContent.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            ParsedToolInfo preview = toolRegistrationService.previewToolFromContent(toolId, openApiContent, path, httpMethod);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ToolDefinition>> getAllTools(
            @RequestParam(required = false) String categoryId) {
        if (categoryId != null && !categoryId.isBlank()) {
            return ResponseEntity.ok(toolRegistrationService.getToolsByCategory(categoryId));
        }
        return ResponseEntity.ok(toolRegistrationService.getAllTools());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolDefinition> getToolById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(toolRegistrationService.getToolById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/by-tool-id/{toolId}")
    public ResponseEntity<ToolDefinition> getToolByToolId(@PathVariable String toolId) {
        try {
            return ResponseEntity.ok(toolRegistrationService.getToolByToolId(toolId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAllTools() {
        try {
            int count = toolRegistrationService.deleteAllTools();
            return ResponseEntity.ok(Map.of("success", true, "deletedCount", count, "message", "All tools deleted"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete all tools: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTool(@PathVariable Long id) {
        try {
            toolRegistrationService.deleteTool(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/by-tool-id/{toolId}")
    public ResponseEntity<Void> deleteToolByToolId(@PathVariable String toolId) {
        try {
            toolRegistrationService.deleteToolByToolId(toolId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update a tool's description and/or category.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTool(@PathVariable Long id, @RequestBody ToolUpdateRequest request) {
        try {
            ToolDefinition tool = toolRegistrationService.updateTool(id, request);
            return ResponseEntity.ok(tool);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Tool update request DTO.
     */
    public record ToolUpdateRequest(
            String description,
            String humanReadableDescription,
            String categoryId
    ) {}

    /**
     * Update a tool by toolId (string identifier).
     */
    @PutMapping("/by-tool-id/{toolId}")
    public ResponseEntity<?> updateToolByToolId(@PathVariable String toolId, @RequestBody ToolUpdateRequest request) {
        try {
            ToolDefinition tool = toolRegistrationService.updateToolByToolId(toolId, request);
            return ResponseEntity.ok(tool);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a parameter's human-readable description.
     */
    @PutMapping("/{toolId}/parameters/{parameterId}/description")
    public ResponseEntity<?> updateParameterDescription(
            @PathVariable String toolId,
            @PathVariable Long parameterId,
            @RequestBody ParameterUpdateRequest request) {
        try {
            toolRegistrationService.updateParameterHumanDescription(parameterId, request.humanReadableDescription());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a parameter's example value.
     */
    @PutMapping("/{toolId}/parameters/{parameterId}/example")
    public ResponseEntity<?> updateParameterExample(
            @PathVariable String toolId,
            @PathVariable Long parameterId,
            @RequestBody ParameterUpdateRequest request) {
        try {
            toolRegistrationService.updateParameterExample(parameterId, request.example());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a parameter's enum values.
     */
    @PutMapping("/{toolId}/parameters/{parameterId}/enum")
    public ResponseEntity<?> updateParameterEnumValues(
            @PathVariable String toolId,
            @PathVariable Long parameterId,
            @RequestBody ParameterUpdateRequest request) {
        try {
            toolRegistrationService.updateParameterEnumValues(parameterId, request.enumValues());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Full parameter update - description, example, and enum values.
     */
    @PutMapping("/{toolId}/parameters/{parameterId}")
    public ResponseEntity<?> updateParameter(
            @PathVariable String toolId,
            @PathVariable Long parameterId,
            @RequestBody ParameterUpdateRequest request) {
        try {
            toolRegistrationService.updateParameter(parameterId, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a response's human-readable description.
     */
    @PutMapping("/{toolId}/responses/{responseId}/description")
    public ResponseEntity<?> updateResponseDescription(
            @PathVariable String toolId,
            @PathVariable Long responseId,
            @RequestBody ResponseUpdateRequest request) {
        try {
            toolRegistrationService.updateResponseHumanDescription(responseId, request.humanReadableDescription());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add test response parameters to a tool for testing nested display.
     */
    @PostMapping("/{toolId}/test-response-params")
    public ResponseEntity<?> addTestResponseParams(@PathVariable String toolId) {
        try {
            toolRegistrationService.addTestResponseParameters(toolId);
            return ResponseEntity.ok(Map.of("message", "Test response parameters added to " + toolId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/openapi/paths")
    public ResponseEntity<Map<String, Object>> getOpenApiPaths(@RequestParam String openApiUrl) {
        try {
            OpenAPI openAPI = new OpenAPIV3Parser().read(openApiUrl);
            if (openAPI == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse OpenAPI specification"));
            }

            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> paths = new ArrayList<>();

            if (openAPI.getPaths() != null) {
                openAPI.getPaths().forEach((path, pathItem) -> {
                    Map<String, Object> pathInfo = new HashMap<>();
                    pathInfo.put("path", path);
                    pathInfo.put("operations", getOperationsInfo(pathItem));
                    paths.add(pathInfo);
                });
            }

            result.put("paths", paths);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error parsing OpenAPI: " + e.getMessage()));
        }
    }

    private List<Map<String, String>> getOperationsInfo(PathItem pathItem) {
        List<Map<String, String>> operations = new ArrayList<>();
        if (pathItem.getGet() != null) {
            operations.add(Map.of(
                "method", "GET",
                "operationId", pathItem.getGet().getOperationId() != null ? pathItem.getGet().getOperationId() : "",
                "summary", pathItem.getGet().getSummary() != null ? pathItem.getGet().getSummary() : ""
            ));
        }
        if (pathItem.getPost() != null) {
            operations.add(Map.of(
                "method", "POST",
                "operationId", pathItem.getPost().getOperationId() != null ? pathItem.getPost().getOperationId() : "",
                "summary", pathItem.getPost().getSummary() != null ? pathItem.getPost().getSummary() : ""
            ));
        }
        if (pathItem.getPut() != null) {
            operations.add(Map.of(
                "method", "PUT",
                "operationId", pathItem.getPut().getOperationId() != null ? pathItem.getPut().getOperationId() : "",
                "summary", pathItem.getPut().getSummary() != null ? pathItem.getPut().getSummary() : ""
            ));
        }
        if (pathItem.getDelete() != null) {
            operations.add(Map.of(
                "method", "DELETE",
                "operationId", pathItem.getDelete().getOperationId() != null ? pathItem.getDelete().getOperationId() : "",
                "summary", pathItem.getDelete().getSummary() != null ? pathItem.getDelete().getSummary() : ""
            ));
        }
        if (pathItem.getPatch() != null) {
            operations.add(Map.of(
                "method", "PATCH",
                "operationId", pathItem.getPatch().getOperationId() != null ? pathItem.getPatch().getOperationId() : "",
                "summary", pathItem.getPatch().getSummary() != null ? pathItem.getPatch().getSummary() : ""
            ));
        }
        return operations;
    }

    @GetMapping("/check-duplicate")
    public ResponseEntity<Map<String, Boolean>> checkDuplicate(
            @RequestParam String openApiEndpoint,
            @RequestParam String path,
            @RequestParam String httpMethod) {
        boolean exists = toolRegistrationService.toolExists(openApiEndpoint, path, httpMethod);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * Parse OpenAPI content (from file upload) and return available paths.
     */
    @PostMapping("/openapi/parse-content")
    public ResponseEntity<Map<String, Object>> parseOpenApiContent(@RequestBody Map<String, String> request) {
        try {
            String content = request.get("content");
            if (content == null || content.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
            }

            OpenAPI openAPI = new OpenAPIV3Parser().readContents(content).getOpenAPI();
            if (openAPI == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse OpenAPI specification from content"));
            }

            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> paths = new ArrayList<>();

            if (openAPI.getPaths() != null) {
                openAPI.getPaths().forEach((path, pathItem) -> {
                    Map<String, Object> pathInfo = new HashMap<>();
                    pathInfo.put("path", path);
                    pathInfo.put("operations", getOperationsInfo(pathItem));
                    paths.add(pathInfo);
                });
            }

            result.put("paths", paths);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error parsing OpenAPI content: " + e.getMessage()));
        }
    }

    /**
     * Register all tools from an OpenAPI URL.
     */
    @PostMapping("/register-openapi")
    public ResponseEntity<Map<String, Object>> registerFromOpenApiUrl(@RequestBody Map<String, String> request) {
        try {
            String openApiUrl = request.get("openApiUrl");
            String categoryId = request.get("categoryId");

            if (openApiUrl == null || openApiUrl.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "openApiUrl is required"));
            }

            OpenAPI openAPI = new OpenAPIV3Parser().read(openApiUrl);
            if (openAPI == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse OpenAPI specification from URL"));
            }

            // Determine base URL from OpenAPI servers or fallback
            String baseUrl = extractBaseUrl(openAPI, openApiUrl);

            List<ToolDefinition> registeredTools = registerToolsFromOpenApi(openAPI, baseUrl, categoryId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "toolsRegistered", registeredTools.size(),
                    "tools", registeredTools.stream().map(ToolDefinition::getToolId).toList()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error registering from OpenAPI: " + e.getMessage()));
        }
    }

    /**
     * Register all tools from OpenAPI content (file upload).
     */
    @PostMapping("/register-openapi-content")
    public ResponseEntity<Map<String, Object>> registerFromOpenApiContent(@RequestBody Map<String, String> request) {
        try {
            String openApiContent = request.get("openApiContent");
            String baseUrl = request.get("baseUrl");
            String categoryId = request.get("categoryId");
            String filename = request.get("filename");

            if (openApiContent == null || openApiContent.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "openApiContent is required"));
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "baseUrl is required"));
            }

            // Parse the OpenAPI content (supports both JSON and YAML)
            OpenAPI openAPI = new OpenAPIV3Parser().readContents(openApiContent).getOpenAPI();
            if (openAPI == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse OpenAPI specification from content"));
            }

            List<ToolDefinition> registeredTools = registerToolsFromOpenApi(openAPI, baseUrl, categoryId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "toolsRegistered", registeredTools.size(),
                    "tools", registeredTools.stream().map(ToolDefinition::getToolId).toList(),
                    "filename", filename != null ? filename : "unknown"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error registering from OpenAPI content: " + e.getMessage()));
        }
    }

    /**
     * Extract base URL from OpenAPI servers or fallback to parsing the spec URL.
     */
    private String extractBaseUrl(OpenAPI openAPI, String openApiUrl) {
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            return openAPI.getServers().get(0).getUrl();
        }
        // Fallback: extract base from OpenAPI URL (e.g., http://localhost:8083/v3/api-docs -> http://localhost:8083)
        try {
            java.net.URI uri = new java.net.URI(openApiUrl);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return openApiUrl;
        }
    }

    /**
     * Register tools from parsed OpenAPI specification.
     */
    private List<ToolDefinition> registerToolsFromOpenApi(OpenAPI openAPI, String baseUrl, String categoryId) {
        List<ToolDefinition> registeredTools = new ArrayList<>();

        if (openAPI.getPaths() == null) {
            return registeredTools;
        }

        openAPI.getPaths().forEach((path, pathItem) -> {
            // Process each HTTP method
            processOperation(pathItem.getGet(), "GET", path, baseUrl, categoryId, openAPI, registeredTools);
            processOperation(pathItem.getPost(), "POST", path, baseUrl, categoryId, openAPI, registeredTools);
            processOperation(pathItem.getPut(), "PUT", path, baseUrl, categoryId, openAPI, registeredTools);
            processOperation(pathItem.getDelete(), "DELETE", path, baseUrl, categoryId, openAPI, registeredTools);
            processOperation(pathItem.getPatch(), "PATCH", path, baseUrl, categoryId, openAPI, registeredTools);
        });

        return registeredTools;
    }

    private void processOperation(io.swagger.v3.oas.models.Operation operation, String method, String path,
                                   String baseUrl, String categoryId, OpenAPI openAPI,
                                   List<ToolDefinition> registeredTools) {
        if (operation == null) return;

        try {
            String operationId = operation.getOperationId();
            if (operationId == null || operationId.isBlank()) {
                // Generate an operation ID from method + path
                operationId = method.toLowerCase() + "_" + path.replaceAll("[^a-zA-Z0-9]", "_");
            }

            String description = operation.getSummary();
            if (description == null || description.isBlank()) {
                description = operation.getDescription();
            }
            if (description == null) {
                description = operationId;
            }

            // Build input schema from parameters
            Map<String, Object> inputSchema = buildInputSchema(operation, openAPI);

            SimpleToolRequest toolRequest = new SimpleToolRequest(
                    operationId,
                    operationId,
                    description,
                    baseUrl,
                    path,
                    method,
                    categoryId != null && !categoryId.isBlank() ? categoryId : null,
                    inputSchema
            );

            ToolDefinition tool = toolRegistrationService.createSimpleTool(toolRequest);
            registeredTools.add(tool);
        } catch (Exception e) {
            // Log and continue - don't fail entire import for one operation
            System.err.println("Failed to register operation " + method + " " + path + ": " + e.getMessage());
        }
    }

    private Map<String, Object> buildInputSchema(io.swagger.v3.oas.models.Operation operation, OpenAPI openAPI) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        // Add parameters
        if (operation.getParameters() != null) {
            for (var param : operation.getParameters()) {
                Map<String, Object> paramSchema = new HashMap<>();
                paramSchema.put("type", param.getSchema() != null ? param.getSchema().getType() : "string");
                paramSchema.put("description", param.getDescription() != null ? param.getDescription() : param.getName());

                properties.put(param.getName(), paramSchema);

                if (Boolean.TRUE.equals(param.getRequired())) {
                    required.add(param.getName());
                }
            }
        }

        // Add request body properties (simplified)
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            var content = operation.getRequestBody().getContent();
            if (content.get("application/json") != null) {
                var mediaType = content.get("application/json");
                if (mediaType.getSchema() != null && mediaType.getSchema().getProperties() != null) {
                    mediaType.getSchema().getProperties().forEach((name, propSchemaObj) -> {
                        @SuppressWarnings("rawtypes")
                        io.swagger.v3.oas.models.media.Schema propSchema = (io.swagger.v3.oas.models.media.Schema) propSchemaObj;
                        Map<String, Object> paramSchema = new HashMap<>();
                        paramSchema.put("type", propSchema.getType() != null ? propSchema.getType() : "string");
                        paramSchema.put("description", propSchema.getDescription() != null ? propSchema.getDescription() : String.valueOf(name));
                        properties.put(String.valueOf(name), paramSchema);
                    });
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "naag-tool-registry",
                "toolCount", toolRegistrationService.getAllTools().size()
        ));
    }

    private List<String> getAvailableMethods(PathItem pathItem) {
        List<String> methods = new ArrayList<>();
        if (pathItem.getGet() != null) methods.add("GET");
        if (pathItem.getPost() != null) methods.add("POST");
        if (pathItem.getPut() != null) methods.add("PUT");
        if (pathItem.getDelete() != null) methods.add("DELETE");
        if (pathItem.getPatch() != null) methods.add("PATCH");
        return methods;
    }
}
