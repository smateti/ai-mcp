package com.naagi.categoryadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.naagi.categoryadmin.client.RagServiceClient;
import com.naagi.categoryadmin.client.ToolRegistryClient;
import com.naagi.categoryadmin.model.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SetupDataInitializer {

    private final RagServiceClient ragServiceClient;
    private final ToolRegistryClient toolRegistryClient;
    private final CategoryService categoryService;
    private final ResourceLoader resourceLoader;

    @Value("${naagi.setup.enabled:false}")
    private boolean setupEnabled;

    @Value("${naagi.setup.documents-path:../setup-data/documents}")
    private String documentsPath;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        if (!setupEnabled) {
            log.info("Setup data initialization is disabled. Set naagi.setup.enabled=true to enable.");
            return;
        }
        initializeSetupData();
    }

    /**
     * Run setup initialization - can be called manually from controller.
     */
    public void initializeSetupData() {
        log.info("Starting setup data initialization...");

        try {
            // Wait a few seconds for other services to be ready
            Thread.sleep(3000);

            // Upload documents for each category
            uploadDocuments();

            // Register public APIs for each category
            registerPublicApis();

            log.info("Setup data initialization completed successfully!");
        } catch (Exception e) {
            log.error("Error during setup data initialization", e);
        }
    }

    private void uploadDocuments() {
        log.info("Ingesting sample documents directly to RAG...");

        Map<String, String> documentMappings = Map.of(
            "service-development-spring-boot-guide.md", CategoryService.CATEGORY_SERVICE_DEV,
            "batch-development-spring-batch-guide.md", CategoryService.CATEGORY_BATCH_DEV,
            "ui-development-react-guide.md", CategoryService.CATEGORY_UI_DEV,
            "misc-development-devops-guide.md", CategoryService.CATEGORY_MISC_DEV,
            "data-retrieval-api-integration-guide.md", CategoryService.CATEGORY_DATA_RETRIEVAL
        );

        // Resolve path - handle both relative and absolute paths
        Path basePath = Paths.get(documentsPath).toAbsolutePath().normalize();
        log.info("Looking for documents in: {}", basePath);

        if (!Files.exists(basePath)) {
            log.error("Documents directory does not exist: {}", basePath);
            return;
        }

        for (Map.Entry<String, String> entry : documentMappings.entrySet()) {
            String fileName = entry.getKey();
            String categoryId = entry.getValue();

            try {
                Path filePath = basePath.resolve(fileName);
                log.info("Checking file: {}", filePath);

                if (Files.exists(filePath)) {
                    String content = Files.readString(filePath, StandardCharsets.UTF_8);
                    String docId = fileName.replace(".md", "");
                    String title = formatTitle(fileName);

                    log.info("Ingesting document directly: {} ({} chars) to category: {}", docId, content.length(), categoryId);

                    // Use direct ingest endpoint instead of preview flow - documents go directly to RAG
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("title", title);
                    metadata.put("source", "setup-initializer");

                    JsonNode result = ragServiceClient.ingestDocument(docId, content, categoryId, metadata);
                    log.info("Document ingested to RAG: {} -> {}", docId, result);
                } else {
                    log.warn("Document file not found: {}", filePath);
                }
            } catch (Exception e) {
                log.error("Failed to ingest document: {}", fileName, e);
            }
        }
    }

    private String formatTitle(String fileName) {
        return fileName
            .replace(".md", "")
            .replace("-", " ")
            .replace("development", "Development")
            .replace("guide", "Guide")
            .replace("spring", "Spring")
            .replace("boot", "Boot")
            .replace("batch", "Batch")
            .replace("react", "React")
            .replace("devops", "DevOps")
            .replace("api", "API")
            .replace("integration", "Integration")
            .replace("ui", "UI");
    }

    private void registerPublicApis() {
        log.info("Registering public APIs as tools...");

        // Register RAG Query tool for each category (essential for knowledge base search)
        registerRagQueryTools();

        // Service Development APIs
        registerServiceDevelopmentApis();

        // Batch Development APIs
        registerBatchDevelopmentApis();

        // UI Development APIs
        registerUiDevelopmentApis();

        // Data Retrieval APIs
        registerDataRetrievalApis();

        // Misc Development APIs
        registerMiscDevelopmentApis();
    }

    private void registerRagQueryTools() {
        log.info("Registering RAG query tools for all categories...");

        // RAG query is the primary tool for knowledge-based questions
        // Each category gets its own rag_query tool that searches its knowledge base
        String ragUrl = "http://localhost:8080";

        // Service Development RAG Query
        registerTool(Tool.builder()
            .id("rag_query")
            .name("RAG Query - Knowledge Base Search")
            .description("Search the knowledge base to answer questions about concepts, best practices, how-to guides, features, and general knowledge. Use this for questions like: 'What is...', 'How to...', 'Explain...', 'How many...', 'Best practices for...'")
            .serviceUrl(ragUrl)
            .method("POST")
            .path("/api/rag/query")
            .categoryId(CategoryService.CATEGORY_SERVICE_DEV)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "question", Map.of("type", "string", "description", "The question to search for in the knowledge base"),
                    "categoryId", Map.of("type", "string", "description", "Category to search in"),
                    "topK", Map.of("type", "integer", "description", "Number of results to return", "default", 5)
                ),
                "required", List.of("question")
            ))
            .build());

        // Copy RAG query to other categories
        for (String categoryId : List.of(
                CategoryService.CATEGORY_BATCH_DEV,
                CategoryService.CATEGORY_UI_DEV,
                CategoryService.CATEGORY_MISC_DEV,
                CategoryService.CATEGORY_DATA_RETRIEVAL)) {
            registerTool(Tool.builder()
                .id("rag_query_" + categoryId.replace("-", "_"))
                .name("RAG Query - Knowledge Base Search")
                .description("Search the knowledge base to answer questions about concepts, best practices, how-to guides, features, and general knowledge. Use this for questions like: 'What is...', 'How to...', 'Explain...', 'How many...', 'Best practices for...'")
                .serviceUrl(ragUrl)
                .method("POST")
                .path("/api/rag/query")
                .categoryId(categoryId)
                .active(true)
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "question", Map.of("type", "string", "description", "The question to search for in the knowledge base"),
                        "categoryId", Map.of("type", "string", "description", "Category to search in"),
                        "topK", Map.of("type", "integer", "description", "Number of results to return", "default", 5)
                    ),
                    "required", List.of("question")
                ))
                .build());
        }
    }

    private void registerServiceDevelopmentApis() {
        String categoryId = CategoryService.CATEGORY_SERVICE_DEV;

        // Spring Initializr API
        registerTool(Tool.builder()
            .id("spring-initializr")
            .name("Spring Initializr")
            .description("Generate Spring Boot projects with specified dependencies and configurations")
            .serviceUrl("https://start.spring.io")
            .method("GET")
            .path("/starter.zip")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "type", Map.of("type", "string", "description", "Project type: maven-project or gradle-project"),
                    "language", Map.of("type", "string", "description", "Language: java, kotlin, or groovy"),
                    "bootVersion", Map.of("type", "string", "description", "Spring Boot version"),
                    "dependencies", Map.of("type", "string", "description", "Comma-separated list of dependencies")
                )
            ))
            .build());

        // Maven Central Search API
        registerTool(Tool.builder()
            .id("maven-central-search")
            .name("Maven Central Search")
            .description("Search for Maven artifacts and dependencies in Maven Central repository")
            .serviceUrl("https://search.maven.org")
            .method("GET")
            .path("/solrsearch/select")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "q", Map.of("type", "string", "description", "Search query (e.g., g:org.springframework)"),
                    "rows", Map.of("type", "integer", "description", "Number of results to return")
                )
            ))
            .build());
    }

    private void registerBatchDevelopmentApis() {
        String categoryId = CategoryService.CATEGORY_BATCH_DEV;

        // Cron Expression Parser API (conceptual - no real public API, but useful pattern)
        registerTool(Tool.builder()
            .id("cron-parser")
            .name("Cron Expression Parser")
            .description("Parse and validate cron expressions for batch job scheduling")
            .serviceUrl("https://crontab.guru")
            .method("GET")
            .path("/api")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "expression", Map.of("type", "string", "description", "Cron expression to parse")
                )
            ))
            .build());

        // JSONPlaceholder Users API - for testing batch data processing
        registerTool(Tool.builder()
            .id("jsonplaceholder-users")
            .name("JSONPlaceholder Users")
            .description("Fetch user information (name, email, phone, address) by user ID. Use this when asked about a specific user with an ID number like 'user 1', 'user id 6', 'what is user 5 name'.")
            .serviceUrl("https://jsonplaceholder.typicode.com")
            .method("GET")
            .path("/users/{id}")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "id", Map.of("type", "integer", "description", "The user ID to look up (1-10)")
                ),
                "required", List.of("id")
            ))
            .build());

        // JSONPlaceholder Posts API - for testing batch data processing
        registerTool(Tool.builder()
            .id("jsonplaceholder-posts")
            .name("JSONPlaceholder Posts")
            .description("Get posts from JSONPlaceholder test API. Can filter by user ID to get posts by a specific user.")
            .serviceUrl("https://jsonplaceholder.typicode.com")
            .method("GET")
            .path("/posts")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "userId", Map.of("type", "integer", "description", "Filter posts by user ID"),
                    "_limit", Map.of("type", "integer", "description", "Limit number of results")
                )
            ))
            .build());
    }

    private void registerUiDevelopmentApis() {
        String categoryId = CategoryService.CATEGORY_UI_DEV;

        // NPM Registry API
        registerTool(Tool.builder()
            .id("npm-registry")
            .name("NPM Registry")
            .description("Search and get information about npm packages for frontend development")
            .serviceUrl("https://registry.npmjs.org")
            .method("GET")
            .path("/{package}")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "package", Map.of("type", "string", "description", "Package name to look up")
                )
            ))
            .build());

        // CDN.js API
        registerTool(Tool.builder()
            .id("cdnjs-api")
            .name("cdnjs Library Search")
            .description("Search for JavaScript libraries on cdnjs CDN")
            .serviceUrl("https://api.cdnjs.com")
            .method("GET")
            .path("/libraries")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "search", Map.of("type", "string", "description", "Library name to search"),
                    "fields", Map.of("type", "string", "description", "Fields to return (e.g., name,version)")
                )
            ))
            .build());

        // Color API
        registerTool(Tool.builder()
            .id("color-api")
            .name("Color API")
            .description("Get color information, schemes, and conversions for UI design")
            .serviceUrl("https://www.thecolorapi.com")
            .method("GET")
            .path("/id")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "hex", Map.of("type", "string", "description", "Hex color code without #"),
                    "format", Map.of("type", "string", "description", "Output format: json, html, svg")
                )
            ))
            .build());
    }

    private void registerDataRetrievalApis() {
        String categoryId = CategoryService.CATEGORY_DATA_RETRIEVAL;

        // OpenWeatherMap API
        registerTool(Tool.builder()
            .id("openweathermap")
            .name("OpenWeatherMap")
            .description("Get current weather data and forecasts for any location")
            .serviceUrl("https://api.openweathermap.org")
            .method("GET")
            .path("/data/2.5/weather")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "q", Map.of("type", "string", "description", "City name, state code and country code"),
                    "appid", Map.of("type", "string", "description", "API key"),
                    "units", Map.of("type", "string", "description", "Units: standard, metric, imperial")
                ),
                "required", List.of("q", "appid")
            ))
            .build());

        // REST Countries API
        registerTool(Tool.builder()
            .id("rest-countries")
            .name("REST Countries")
            .description("Get information about countries including population, languages, currencies")
            .serviceUrl("https://restcountries.com")
            .method("GET")
            .path("/v3.1/name/{name}")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Country name to search")
                ),
                "required", List.of("name")
            ))
            .build());

        // Exchange Rates API
        registerTool(Tool.builder()
            .id("exchange-rates")
            .name("Exchange Rates API")
            .description("Get current and historical exchange rates for currencies")
            .serviceUrl("https://api.exchangerate-api.com")
            .method("GET")
            .path("/v4/latest/{base}")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "base", Map.of("type", "string", "description", "Base currency code (e.g., USD, EUR)")
                ),
                "required", List.of("base")
            ))
            .build());

        // JSONPlaceholder (for testing)
        registerTool(Tool.builder()
            .id("jsonplaceholder")
            .name("JSONPlaceholder")
            .description("Fake REST API for testing and prototyping - returns sample data")
            .serviceUrl("https://jsonplaceholder.typicode.com")
            .method("GET")
            .path("/posts")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "userId", Map.of("type", "integer", "description", "Filter by user ID"),
                    "_limit", Map.of("type", "integer", "description", "Limit number of results")
                )
            ))
            .build());

        // IP API
        registerTool(Tool.builder()
            .id("ip-api")
            .name("IP Geolocation API")
            .description("Get geolocation data from IP addresses including country, city, ISP")
            .serviceUrl("http://ip-api.com")
            .method("GET")
            .path("/json/{ip}")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "ip", Map.of("type", "string", "description", "IP address to lookup"),
                    "fields", Map.of("type", "string", "description", "Comma-separated list of fields to return")
                )
            ))
            .build());
    }

    private void registerMiscDevelopmentApis() {
        String categoryId = CategoryService.CATEGORY_MISC_DEV;

        // GitHub API
        registerTool(Tool.builder()
            .id("github-api")
            .name("GitHub API")
            .description("Access GitHub repositories, issues, pull requests, and user information")
            .serviceUrl("https://api.github.com")
            .method("GET")
            .path("/repos/{owner}/{repo}")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "owner", Map.of("type", "string", "description", "Repository owner username"),
                    "repo", Map.of("type", "string", "description", "Repository name")
                ),
                "required", List.of("owner", "repo")
            ))
            .build());

        // Docker Hub API
        registerTool(Tool.builder()
            .id("dockerhub-api")
            .name("Docker Hub API")
            .description("Search Docker images and get repository information from Docker Hub")
            .serviceUrl("https://hub.docker.com")
            .method("GET")
            .path("/v2/repositories/{namespace}/{repository}")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "namespace", Map.of("type", "string", "description", "Docker Hub namespace/organization"),
                    "repository", Map.of("type", "string", "description", "Repository name")
                ),
                "required", List.of("namespace", "repository")
            ))
            .build());

        // Random User Generator
        registerTool(Tool.builder()
            .id("random-user")
            .name("Random User Generator")
            .description("Generate random user data for testing - names, emails, addresses, etc.")
            .serviceUrl("https://randomuser.me")
            .method("GET")
            .path("/api")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "results", Map.of("type", "integer", "description", "Number of users to generate"),
                    "nat", Map.of("type", "string", "description", "Nationality filter (e.g., us,gb,fr)")
                )
            ))
            .build());

        // Lorem Ipsum Generator
        registerTool(Tool.builder()
            .id("lorem-ipsum")
            .name("Lorem Ipsum Generator")
            .description("Generate placeholder text for UI mockups and testing")
            .serviceUrl("https://loripsum.net")
            .method("GET")
            .path("/api/{paragraphs}")
            .categoryId(categoryId)
            .active(true)
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "paragraphs", Map.of("type", "integer", "description", "Number of paragraphs to generate")
                )
            ))
            .build());
    }

    private void registerTool(Tool tool) {
        try {
            // Check if tool already exists
            List<Tool> existingTools = toolRegistryClient.getAllTools();
            boolean exists = existingTools.stream()
                .anyMatch(t -> t.getId() != null && t.getId().equals(tool.getId()));

            if (!exists) {
                tool.setCreatedAt(LocalDateTime.now());
                tool.setUpdatedAt(LocalDateTime.now());
                Tool created = toolRegistryClient.createTool(tool);
                log.info("Registered tool: {} ({})", tool.getName(), created.getId());

                // Add tool to category
                categoryService.addToolToCategory(tool.getCategoryId(), tool.getId());
            } else {
                log.debug("Tool already exists: {}", tool.getId());
            }
        } catch (Exception e) {
            log.error("Failed to register tool: {}", tool.getName(), e);
        }
    }
}
