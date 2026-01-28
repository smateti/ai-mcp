package com.naagi.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final String categoryAdminUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ToolRegistryClient(
            @Value("${naagi.services.tool-registry.url}") String toolRegistryUrl,
            @Value("${naagi.services.category-admin.url}") String categoryAdminUrl,
            ObjectMapper objectMapper) {
        this.toolRegistryUrl = toolRegistryUrl;
        this.categoryAdminUrl = categoryAdminUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<JsonNode> getAllTools() {
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
            List<JsonNode> tools = new ArrayList<>();

            if (toolsArray.isArray()) {
                for (JsonNode tool : toolsArray) {
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

    public JsonNode getToolByName(String toolName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(toolRegistryUrl + "/api/tools/by-tool-id/" + toolName))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
        } catch (Exception e) {
            log.error("Error fetching tool {} from registry", toolName, e);
        }
        return null;
    }

    /**
     * Get tools for a category with parameter overrides applied.
     * Fetches merged tools from category-admin which includes locked values and enum restrictions.
     */
    public List<JsonNode> getToolsByCategory(String categoryId) {
        // First try to get merged tools from category-admin (includes overrides)
        List<JsonNode> mergedTools = getMergedToolsFromCategoryAdmin(categoryId);
        if (!mergedTools.isEmpty()) {
            return mergedTools;
        }

        // Fallback to tool-registry if category-admin fails
        log.warn("Falling back to tool-registry for category {} (category-admin unavailable)", categoryId);
        return getToolsFromRegistry(categoryId);
    }

    /**
     * Fetch merged tools from category-admin with parameter overrides applied.
     */
    private List<JsonNode> getMergedToolsFromCategoryAdmin(String categoryId) {
        try {
            String url = categoryAdminUrl + "/api/categories/" + categoryId + "/tools/merged";
            log.debug("Fetching merged tools from category-admin: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Category-admin returned HTTP {} for merged tools, categoryId={}",
                        response.statusCode(), categoryId);
                return List.of();
            }

            JsonNode toolsArray = objectMapper.readTree(response.body());
            List<JsonNode> tools = new ArrayList<>();

            if (toolsArray.isArray()) {
                for (JsonNode tool : toolsArray) {
                    tools.add(tool);
                }
            }

            log.info("Loaded {} merged tools for category {} from category-admin", tools.size(), categoryId);
            return tools;
        } catch (Exception e) {
            log.warn("Error fetching merged tools from category-admin for category {}: {}",
                    categoryId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fallback: fetch tools directly from tool-registry (without overrides).
     */
    private List<JsonNode> getToolsFromRegistry(String categoryId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(toolRegistryUrl + "/api/tools?categoryId=" + categoryId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch tools for category {}: HTTP {}", categoryId, response.statusCode());
                return List.of();
            }

            JsonNode toolsArray = objectMapper.readTree(response.body());
            List<JsonNode> tools = new ArrayList<>();

            if (toolsArray.isArray()) {
                for (JsonNode tool : toolsArray) {
                    tools.add(tool);
                }
            }

            log.info("Loaded {} tools for category {} from registry", tools.size(), categoryId);
            return tools;
        } catch (Exception e) {
            log.error("Error fetching tools for category {} from registry", categoryId, e);
            return List.of();
        }
    }

    /**
     * Get a merged tool by name for a specific category (includes overrides).
     */
    public JsonNode getMergedToolByName(String categoryId, String toolName) {
        try {
            String url = categoryAdminUrl + "/api/categories/" + categoryId + "/tools/" + toolName + "/merged";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
        } catch (Exception e) {
            log.error("Error fetching merged tool {} for category {}", toolName, categoryId, e);
        }
        return null;
    }
}
