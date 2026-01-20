package com.example.mcp.service;

import com.example.mcp.dto.RegistryParameterDefinition;
import com.example.mcp.dto.RegistryToolDefinition;
import com.example.mcp.model.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class ToolRegistry {
    private final Map<String, Tool> tools = new HashMap<>();
    private final Map<String, Function<JsonNode, Object>> toolExecutors = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final ToolRegistryClient registryClient;
    private final boolean loadFromRegistry;

    public ToolRegistry(
            ObjectMapper objectMapper,
            ToolRegistryClient registryClient,
            @Value("${tool.registry.enabled:true}") boolean loadFromRegistry) {
        this.objectMapper = objectMapper;
        this.registryClient = registryClient;
        this.loadFromRegistry = loadFromRegistry;
    }

    @PostConstruct
    public void initialize() {
        registerDefaultTools();
        registerTestApiTool();  // Add test tool for REST call demonstration
        registerRagTools();  // Add RAG tools for document ingestion and querying
        if (loadFromRegistry) {
            loadToolsFromRegistry();
        }
    }

    private void registerDefaultTools() {
        registerTool(
            "echo",
            "Echoes back the input message",
            createEchoSchema(),
            this::executeEcho
        );

        registerTool(
            "add",
            "Adds two numbers together",
            createAddSchema(),
            this::executeAdd
        );

        registerTool(
            "get_current_time",
            "Returns the current server time",
            createEmptySchema(),
            this::executeGetCurrentTime
        );
    }

    private void registerTestApiTool() {
        // Create a test tool for JSONPlaceholder public API
        RegistryToolDefinition testTool = new RegistryToolDefinition();
        testTool.setToolId("jsonplaceholder-user");
        testTool.setName("Get User from JSONPlaceholder");
        testTool.setDescription("Fetches user information from JSONPlaceholder testing API");
        testTool.setHumanReadableDescription("Retrieves user details by ID from the JSONPlaceholder public testing API. Returns user information including name, email, address, phone, website, and company details. Valid user IDs are 1-10.");
        testTool.setBaseUrl("https://jsonplaceholder.typicode.com");
        testTool.setPath("/users/{userId}");
        testTool.setHttpMethod("GET");

        // Create path parameter
        RegistryParameterDefinition userIdParam = new RegistryParameterDefinition();
        userIdParam.setName("userId");
        userIdParam.setType("integer");
        userIdParam.setRequired(true);
        userIdParam.setIn("path");
        userIdParam.setDescription("User ID");
        userIdParam.setHumanReadableDescription("The ID of the user to retrieve. Valid values are 1 through 10.");
        userIdParam.setExample("1");

        List<RegistryParameterDefinition> params = new ArrayList<>();
        params.add(userIdParam);
        testTool.setParameters(params);

        // Convert to JSON schema and register
        JsonNode schema = convertRegistryToolToSchema(testTool);
        registerTool(
            testTool.getToolId(),
            testTool.getHumanReadableDescription(),
            schema,
            args -> executeRegistryTool(testTool, args)
        );

        log.info("Registered test API tool: {} - {} {}",
            testTool.getToolId(),
            testTool.getHttpMethod(),
            testTool.getBaseUrl() + testTool.getPath());
    }

    private void registerRagTools() {
        // Register rag_ingest tool
        RegistryToolDefinition ragIngest = new RegistryToolDefinition();
        ragIngest.setToolId("rag_ingest");
        ragIngest.setName("Ingest Document");
        ragIngest.setDescription("Ingest a document into the RAG system");
        ragIngest.setHumanReadableDescription("Ingest a document into the RAG knowledge base. This tool accepts a document ID and full text content, automatically chunks the document, generates embeddings, and stores them in the vector database. Use this before querying documents.");
        ragIngest.setBaseUrl("http://localhost:8080");
        ragIngest.setPath("/api/rag/ingest");
        ragIngest.setHttpMethod("POST");

        // Create parameters for ingest
        RegistryParameterDefinition docIdParam = new RegistryParameterDefinition();
        docIdParam.setName("docId");
        docIdParam.setType("string");
        docIdParam.setRequired(true);
        docIdParam.setIn("body");
        docIdParam.setDescription("Unique identifier for the document");
        docIdParam.setHumanReadableDescription("A unique identifier for this document (e.g., filename or title)");

        RegistryParameterDefinition textParam = new RegistryParameterDefinition();
        textParam.setName("text");
        textParam.setType("string");
        textParam.setRequired(true);
        textParam.setIn("body");
        textParam.setDescription("Full text content of the document");
        textParam.setHumanReadableDescription("The complete text content of the document to be ingested");

        List<RegistryParameterDefinition> ingestParams = new ArrayList<>();
        ingestParams.add(docIdParam);
        ingestParams.add(textParam);
        ragIngest.setParameters(ingestParams);

        JsonNode ingestSchema = convertRegistryToolToSchema(ragIngest);
        registerTool(
            ragIngest.getToolId(),
            ragIngest.getHumanReadableDescription(),
            ingestSchema,
            args -> executeRegistryTool(ragIngest, args)
        );

        log.info("Registered RAG tool: {} - {} {}",
            ragIngest.getToolId(),
            ragIngest.getHttpMethod(),
            ragIngest.getBaseUrl() + ragIngest.getPath());

        // Register rag_query tool
        RegistryToolDefinition ragQuery = new RegistryToolDefinition();
        ragQuery.setToolId("rag_query");
        ragQuery.setName("Query Documents");
        ragQuery.setDescription("Query the RAG system with a question");
        ragQuery.setHumanReadableDescription("Query the RAG knowledge base with a natural language question. This tool searches for relevant document chunks, retrieves context, and generates an AI-powered answer with source citations. Use this for any knowledge questions, how-to questions, or documentation lookups.");
        ragQuery.setBaseUrl("http://localhost:8080");
        ragQuery.setPath("/api/rag/query");
        ragQuery.setHttpMethod("POST");

        // Create parameters for query
        RegistryParameterDefinition questionParam = new RegistryParameterDefinition();
        questionParam.setName("question");
        questionParam.setType("string");
        questionParam.setRequired(true);
        questionParam.setIn("body");
        questionParam.setDescription("The question to answer");
        questionParam.setHumanReadableDescription("The question you want answered based on ingested documents");

        RegistryParameterDefinition topKParam = new RegistryParameterDefinition();
        topKParam.setName("topK");
        topKParam.setType("integer");
        topKParam.setRequired(false);
        topKParam.setIn("body");
        topKParam.setDescription("Number of top chunks to retrieve (default 5)");
        topKParam.setHumanReadableDescription("Number of most relevant chunks to retrieve (default 5, higher values provide more context)");
        topKParam.setDefaultValue("5");

        RegistryParameterDefinition categoryParam = new RegistryParameterDefinition();
        categoryParam.setName("category");
        categoryParam.setType("string");
        categoryParam.setRequired(false);
        categoryParam.setIn("body");
        categoryParam.setDescription("Category to filter documents by (e.g., 'service-dev', 'batch-dev')");
        categoryParam.setHumanReadableDescription("Optional category to filter documents. Use 'service-dev' for Service Development, 'batch-dev' for Batch Development.");

        List<RegistryParameterDefinition> queryParams = new ArrayList<>();
        queryParams.add(questionParam);
        queryParams.add(topKParam);
        queryParams.add(categoryParam);
        ragQuery.setParameters(queryParams);

        JsonNode querySchema = convertRegistryToolToSchema(ragQuery);
        registerTool(
            ragQuery.getToolId(),
            ragQuery.getHumanReadableDescription(),
            querySchema,
            args -> executeRegistryTool(ragQuery, args)
        );

        log.info("Registered RAG tool: {} - {} {}",
            ragQuery.getToolId(),
            ragQuery.getHttpMethod(),
            ragQuery.getBaseUrl() + ragQuery.getPath());
    }

    public void registerTool(String name, String description, JsonNode schema,
                            Function<JsonNode, Object> executor) {
        Tool tool = new Tool(name, description, schema);
        tools.put(name, tool);
        toolExecutors.put(name, executor);
    }

    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    public Object executeTool(String name, JsonNode arguments) {
        Function<JsonNode, Object> executor = toolExecutors.get(name);
        if (executor == null) {
            throw new IllegalArgumentException("Tool not found: " + name);
        }
        return executor.apply(arguments);
    }

    private Object executeEcho(JsonNode args) {
        String message = args.get("message").asText();
        return Map.of("echo", message);
    }

    private Object executeAdd(JsonNode args) {
        double a = args.get("a").asDouble();
        double b = args.get("b").asDouble();
        return Map.of("result", a + b);
    }

    private Object executeGetCurrentTime(JsonNode args) {
        return Map.of("time", java.time.Instant.now().toString());
    }

    private JsonNode createEchoSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode messageProperty = objectMapper.createObjectNode();
        messageProperty.put("type", "string");
        messageProperty.put("description", "The message to echo back");
        properties.set("message", messageProperty);

        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode().add("message"));

        return schema;
    }

    private JsonNode createAddSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode aProperty = objectMapper.createObjectNode();
        aProperty.put("type", "number");
        aProperty.put("description", "First number");
        properties.set("a", aProperty);

        ObjectNode bProperty = objectMapper.createObjectNode();
        bProperty.put("type", "number");
        bProperty.put("description", "Second number");
        properties.set("b", bProperty);

        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode().add("a").add("b"));

        return schema;
    }

    private JsonNode createEmptySchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    /**
     * Refresh tools from the dynamic tool registry.
     * This method can be called on-demand to reload the latest tools.
     */
    public void refreshToolsFromRegistry() {
        loadToolsFromRegistry();
    }

    private void loadToolsFromRegistry() {
        try {
            log.info("Loading tools from dynamic tool registry...");
            List<RegistryToolDefinition> registryTools = registryClient.fetchAllTools();

            for (RegistryToolDefinition registryTool : registryTools) {
                JsonNode schema = convertRegistryToolToSchema(registryTool);
                String toolName = registryTool.getToolId();

                // Prefer humanReadableDescription, then description, then fallback
                String description;
                if (registryTool.getHumanReadableDescription() != null && !registryTool.getHumanReadableDescription().isEmpty()) {
                    description = registryTool.getHumanReadableDescription();
                } else if (registryTool.getDescription() != null && !registryTool.getDescription().isEmpty()) {
                    description = registryTool.getDescription();
                } else {
                    description = "Tool from registry: " + registryTool.getName();
                }

                registerTool(toolName, description, schema, args -> executeRegistryTool(registryTool, args));
                log.info("Registered tool from registry: {} ({}) - {}", toolName, registryTool.getName(),
                        registryTool.getBaseUrl() != null ? registryTool.getBaseUrl() + registryTool.getPath() : registryTool.getPath());
            }

            log.info("Loaded {} tools from registry", registryTools.size());
        } catch (Exception e) {
            log.error("Failed to load tools from registry: {}", e.getMessage(), e);
        }
    }

    private JsonNode convertRegistryToolToSchema(RegistryToolDefinition registryTool) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode required = objectMapper.createArrayNode();

        if (registryTool.getParameters() != null) {
            for (RegistryParameterDefinition param : registryTool.getParameters()) {
                convertParameterToProperty(param, properties, required);
            }
        }

        schema.set("properties", properties);
        schema.set("required", required);

        return schema;
    }

    private void convertParameterToProperty(RegistryParameterDefinition param, ObjectNode properties, ArrayNode required) {
        ObjectNode property = objectMapper.createObjectNode();

        // Map type
        String type = mapTypeToJsonSchema(param.getType());
        property.put("type", type);

        // Add description
        String description = param.getHumanReadableDescription() != null
                ? param.getHumanReadableDescription()
                : (param.getDescription() != null ? param.getDescription() : "");
        property.put("description", description);

        // Add format if present
        if (param.getFormat() != null) {
            property.put("format", param.getFormat());
        }

        // Add example if present
        if (param.getExample() != null) {
            property.put("example", param.getExample());
        }

        // Add default value if present
        if (param.getDefaultValue() != null) {
            property.put("default", param.getDefaultValue());
        }

        // Add enum values if present
        if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
            ArrayNode enumArray = objectMapper.createArrayNode();
            for (String enumValue : param.getEnumValues()) {
                enumArray.add(enumValue);
            }
            property.set("enum", enumArray);
        }

        // Handle array items
        if (param.getType() != null && param.getType().startsWith("array[")) {
            property.put("type", "array");
            ObjectNode items = objectMapper.createObjectNode();
            String itemType = param.getType().substring(6, param.getType().length() - 1);
            items.put("type", mapTypeToJsonSchema(itemType));
            property.set("items", items);
        }

        // Handle nested parameters
        if (param.getNestedParameters() != null && !param.getNestedParameters().isEmpty()) {
            ObjectNode nestedProperties = objectMapper.createObjectNode();
            ArrayNode nestedRequired = objectMapper.createArrayNode();

            for (RegistryParameterDefinition nestedParam : param.getNestedParameters()) {
                convertParameterToProperty(nestedParam, nestedProperties, nestedRequired);
            }

            property.set("properties", nestedProperties);
            if (nestedRequired.size() > 0) {
                property.set("required", nestedRequired);
            }
        }

        properties.set(param.getName(), property);

        if (param.getRequired()) {
            required.add(param.getName());
        }
    }

    private String mapTypeToJsonSchema(String registryType) {
        if (registryType == null) {
            return "string";
        }

        // Handle object references like "object (PropertySet)"
        if (registryType.startsWith("object")) {
            return "object";
        }

        // Handle array types like "array[Property]"
        if (registryType.startsWith("array")) {
            return "array";
        }

        // Map common types
        return switch (registryType.toLowerCase()) {
            case "integer", "int" -> "integer";
            case "number", "double", "float" -> "number";
            case "boolean", "bool" -> "boolean";
            case "string" -> "string";
            default -> "string";
        };
    }

    private Object executeRegistryTool(RegistryToolDefinition registryTool, JsonNode args) {
        try {
            // Construct the full URL
            String baseUrl = registryTool.getBaseUrl();
            String path = registryTool.getPath();

            if (baseUrl == null || baseUrl.isEmpty()) {
                return createErrorResponse("Service configuration error", "Base URL is not configured for tool: " + registryTool.getToolId());
            }

            // Replace path parameters with actual values from arguments
            String resolvedPath = resolvePath(path, args);
            String fullUrl = baseUrl + resolvedPath;

            log.info("Executing registry tool: {} - {} {}", registryTool.getToolId(), registryTool.getHttpMethod(), fullUrl);

            // Create WebClient for this request
            org.springframework.web.reactive.function.client.WebClient webClient =
                org.springframework.web.reactive.function.client.WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Accept", "application/json")
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            // Execute based on HTTP method
            String httpMethod = registryTool.getHttpMethod().toUpperCase();
            org.springframework.web.reactive.function.client.WebClient.ResponseSpec response;

            switch (httpMethod) {
                case "GET":
                    response = webClient.get()
                        .uri(resolvedPath)
                        .retrieve();
                    break;
                case "POST":
                    response = executePostPutPatch(webClient.post().uri(resolvedPath), args, registryTool);
                    break;
                case "PUT":
                    response = executePostPutPatch(webClient.put().uri(resolvedPath), args, registryTool);
                    break;
                case "PATCH":
                    response = executePostPutPatch(webClient.patch().uri(resolvedPath), args, registryTool);
                    break;
                case "DELETE":
                    response = webClient.delete()
                        .uri(resolvedPath)
                        .retrieve();
                    break;
                default:
                    return createErrorResponse("Unsupported HTTP method", "HTTP method not supported: " + httpMethod);
            }

            // Handle different status codes
            response = response.onStatus(
                status -> status.is4xxClientError(),
                clientResponse -> clientResponse.bodyToMono(String.class)
                    .map(body -> new RuntimeException("Client error (HTTP " + clientResponse.statusCode().value() + "): " + body))
            ).onStatus(
                status -> status.is5xxServerError(),
                clientResponse -> clientResponse.bodyToMono(String.class)
                    .map(body -> new RuntimeException("Server error (HTTP " + clientResponse.statusCode().value() + "): " + body))
            );

            // Get response body
            String responseBody = response.bodyToMono(String.class).block();

            // Parse response as JSON if possible
            try {
                JsonNode responseJson = objectMapper.readTree(responseBody);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("statusCode", 200);
                result.put("data", responseJson);
                result.put("tool", registryTool.getToolId());
                result.put("endpoint", fullUrl);

                log.info("Successfully executed registry tool: {}", registryTool.getToolId());
                return result;
            } catch (Exception e) {
                // Return as string if not JSON
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("statusCode", 200);
                result.put("data", responseBody);
                result.put("tool", registryTool.getToolId());
                result.put("endpoint", fullUrl);
                return result;
            }

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("HTTP error executing tool {}: {} - {}", registryTool.getToolId(), e.getStatusCode(), e.getMessage());
            return createErrorResponse(
                "Service call failed",
                String.format("HTTP %d: %s. Endpoint: %s %s. Details: %s",
                    e.getStatusCode().value(),
                    e.getStatusText(),
                    registryTool.getHttpMethod(),
                    registryTool.getBaseUrl() + registryTool.getPath(),
                    e.getResponseBodyAsString())
            );
        } catch (Exception e) {
            log.error("Error executing registry tool {}: {}", registryTool.getToolId(), e.getMessage(), e);
            return createErrorResponse(
                "Service execution failed",
                String.format("Failed to execute tool '%s': %s. Endpoint: %s %s",
                    registryTool.getToolId(),
                    e.getMessage(),
                    registryTool.getHttpMethod(),
                    registryTool.getBaseUrl() != null ? registryTool.getBaseUrl() + registryTool.getPath() : registryTool.getPath())
            );
        }
    }

    private org.springframework.web.reactive.function.client.WebClient.ResponseSpec executePostPutPatch(
            org.springframework.web.reactive.function.client.WebClient.RequestBodySpec request,
            JsonNode args,
            RegistryToolDefinition registryTool) {

        // Filter out path parameters from body
        ObjectNode bodyArgs = objectMapper.createObjectNode();
        var iterator = args.fields();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!isPathParameter(entry.getKey(), registryTool)) {
                bodyArgs.set(entry.getKey(), entry.getValue());
            }
        }

        if (bodyArgs.size() > 0) {
            return request.bodyValue(bodyArgs).retrieve();
        } else {
            return request.retrieve();
        }
    }

    private String resolvePath(String path, JsonNode args) {
        if (path == null || args == null) {
            return path;
        }

        String resolvedPath = path;

        // Replace path parameters like {paramName} with actual values
        var iterator = args.fields();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String placeholder = "{" + entry.getKey() + "}";
            if (resolvedPath.contains(placeholder)) {
                resolvedPath = resolvedPath.replace(placeholder, entry.getValue().asText());
            }
        }

        return resolvedPath;
    }

    private boolean isPathParameter(String paramName, RegistryToolDefinition registryTool) {
        if (registryTool.getPath() == null) {
            return false;
        }
        return registryTool.getPath().contains("{" + paramName + "}");
    }

    private Map<String, Object> createErrorResponse(String error, String details) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", error);
        result.put("details", details);
        result.put("timestamp", java.time.Instant.now().toString());
        return result;
    }
}
