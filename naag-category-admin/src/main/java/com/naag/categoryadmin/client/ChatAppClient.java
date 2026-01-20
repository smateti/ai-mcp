package com.naag.categoryadmin.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ChatAppClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ChatAppClient(
            @Value("${naag.services.chat-app.url:http://localhost:8087}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Get audit logs for a user with pagination.
     */
    public List<JsonNode> getAuditLogs(String userId, int page, int size) {
        return getAuditLogs(userId, page, size, null, null);
    }

    /**
     * Get audit logs for a user with pagination and time-based filtering.
     * @param userId The user ID
     * @param page Page number
     * @param size Page size
     * @param lastMinutes Filter to last N minutes (takes precedence over lastHours)
     * @param lastHours Filter to last N hours
     */
    public List<JsonNode> getAuditLogs(String userId, int page, int size, Integer lastMinutes, Integer lastHours) {
        try {
            String encodedUserId = URLEncoder.encode(userId != null ? userId : "default-user", StandardCharsets.UTF_8);
            StringBuilder urlBuilder = new StringBuilder(String.format("%s/api/audit?userId=%s&page=%d&size=%d",
                    baseUrl, encodedUserId, page, size));

            // Add time-based filter parameters
            if (lastMinutes != null) {
                urlBuilder.append("&lastMinutes=").append(lastMinutes);
            } else if (lastHours != null) {
                urlBuilder.append("&lastHours=").append(lastHours);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to get chat audit logs: HTTP {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching chat audit logs", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get all audit logs (for all users).
     */
    public List<JsonNode> getAllAuditLogs(int page, int size) {
        return getAuditLogs("default-user", page, size);
    }

    /**
     * Get audit logs for a specific session.
     */
    public List<JsonNode> getSessionAuditLogs(String sessionId) {
        try {
            String encodedSessionId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
            String url = String.format("%s/api/audit/session/%s", baseUrl, encodedSessionId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to get session audit logs: HTTP {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching session audit logs", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get failed operations for a user.
     */
    public List<JsonNode> getFailedOperations(String userId) {
        try {
            String encodedUserId = URLEncoder.encode(userId != null ? userId : "default-user", StandardCharsets.UTF_8);
            String url = String.format("%s/api/audit/errors?userId=%s", baseUrl, encodedUserId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to get error audit logs: HTTP {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching error audit logs", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get user statistics from the chat app.
     */
    public Map<String, Object> getUserStats(String userId) {
        try {
            String encodedUserId = URLEncoder.encode(userId != null ? userId : "default-user", StandardCharsets.UTF_8);
            String url = String.format("%s/api/stats?userId=%s", baseUrl, encodedUserId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to get user stats: HTTP {}", response.statusCode());
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            log.error("Error fetching user stats", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Check if the chat app is available.
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Chat app not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the base URL of the chat app service.
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}
