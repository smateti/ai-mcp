package com.example.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client to fetch enabled tools and documents from category-admin service
 */
@Service
@Slf4j
public class CategoryAdminClient {

    private final String categoryAdminUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CategoryAdminClient(
            @Value("${category.admin.url:http://localhost:8085}") String categoryAdminUrl,
            ObjectMapper objectMapper) {
        this.categoryAdminUrl = categoryAdminUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        log.info("Category Admin Client configured to connect to: {}", categoryAdminUrl);
    }

    /**
     * Get enabled tool IDs for a category
     */
    public List<String> getEnabledTools(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return List.of();
        }

        try {
            String url = categoryAdminUrl + "/api/categories/" + categoryId + "/tools/enabled";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parse response: {"toolIds": ["tool1", "tool2"]}
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                @SuppressWarnings("unchecked")
                List<String> toolIds = (List<String>) responseMap.get("toolIds");
                log.debug("Fetched {} enabled tools for category {}", toolIds.size(), categoryId);
                return toolIds != null ? toolIds : List.of();
            } else {
                log.warn("Failed to fetch enabled tools for category {}: status {}", categoryId, response.statusCode());
                return List.of();
            }
        } catch (Exception e) {
            log.error("Error fetching enabled tools for category {}", categoryId, e);
            return List.of();
        }
    }

    /**
     * Get enabled document IDs for a category
     */
    public List<String> getEnabledDocuments(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return List.of();
        }

        try {
            String url = categoryAdminUrl + "/api/categories/" + categoryId + "/documents/enabled";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Parse response: {"documentIds": ["doc1", "doc2"]}
                Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
                @SuppressWarnings("unchecked")
                List<String> documentIds = (List<String>) responseMap.get("documentIds");
                log.debug("Fetched {} enabled documents for category {}", documentIds.size(), categoryId);
                return documentIds != null ? documentIds : List.of();
            } else {
                log.warn("Failed to fetch enabled documents for category {}: status {}", categoryId, response.statusCode());
                return List.of();
            }
        } catch (Exception e) {
            log.error("Error fetching enabled documents for category {}", categoryId, e);
            return List.of();
        }
    }
}
