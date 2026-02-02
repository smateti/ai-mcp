package com.naagi.llm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolExecutionService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String executeTool(String url, String method, String argsJson) {
        try {
            Map<String, Object> parameters = Map.of();
            if (argsJson != null && !argsJson.isBlank()) {
                parameters = objectMapper.readValue(argsJson, new TypeReference<>() {});
            }

            // Substitute path parameters like {param}
            String resolvedUrl = url;
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (resolvedUrl.contains(placeholder)) {
                    resolvedUrl = resolvedUrl.replace(placeholder,
                            URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                }
            }

            HttpRequest request;
            if ("GET".equalsIgnoreCase(method)) {
                // Remaining parameters as query string
                StringBuilder query = new StringBuilder();
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    if (url.contains("{" + entry.getKey() + "}")) continue; // already in path
                    if (entry.getValue() == null || String.valueOf(entry.getValue()).isBlank()) continue;
                    if (!query.isEmpty()) query.append("&");
                    query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                            .append("=")
                            .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                }
                String fullUrl = query.isEmpty() ? resolvedUrl : resolvedUrl + "?" + query;
                request = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
            } else {
                // POST: parameters as JSON body
                request = HttpRequest.newBuilder()
                        .uri(URI.create(resolvedUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(parameters)))
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                return "ERROR: HTTP " + response.statusCode() + " - " + response.body();
            }

            return response.body();
        } catch (Exception e) {
            log.error("[TOOL-EXEC] Failed to execute tool at {}: {}", url, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }
}
