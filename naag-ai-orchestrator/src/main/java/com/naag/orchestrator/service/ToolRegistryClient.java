package com.naag.orchestrator.service;

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
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ToolRegistryClient(
            @Value("${naag.services.tool-registry.url}") String toolRegistryUrl,
            ObjectMapper objectMapper) {
        this.toolRegistryUrl = toolRegistryUrl;
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

    public List<JsonNode> getToolsByCategory(String categoryId) {
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
}
