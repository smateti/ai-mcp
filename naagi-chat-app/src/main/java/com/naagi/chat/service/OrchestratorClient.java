package com.naagi.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@Slf4j
public class OrchestratorClient {

    private final String orchestratorUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OrchestratorClient(
            @Value("${naagi.services.orchestrator.url}") String orchestratorUrl,
            ObjectMapper objectMapper) {
        this.orchestratorUrl = orchestratorUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public JsonNode orchestrate(String message, String sessionId, String categoryId) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("message", message);
            request.put("sessionId", sessionId);
            if (categoryId != null) {
                request.put("categoryId", categoryId);
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(orchestratorUrl + "/api/orchestrate"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Orchestrator returned HTTP {}: {}", response.statusCode(), response.body());
                return createErrorResponse("Orchestrator returned HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error calling orchestrator", e);
            return createErrorResponse("Error calling orchestrator: " + e.getMessage());
        }
    }

    private JsonNode createErrorResponse(String errorMessage) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("error", true);
        error.put("response", errorMessage);
        return error;
    }
}
