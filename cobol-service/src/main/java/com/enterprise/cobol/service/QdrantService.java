package com.enterprise.cobol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class QdrantService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${qdrant.collection}")
    private String collectionName;

    public QdrantService(@Value("${qdrant.url}") String qdrantUrl, ObjectMapper objectMapper) {
        this.webClient = WebClient.builder().baseUrl(qdrantUrl).build();
        this.objectMapper = objectMapper;
    }

    public List<SearchResult> search(float[] queryVector, int limit) {
        return search(queryVector, limit, null);
    }

    public List<SearchResult> search(float[] queryVector, int limit, String batchRunId) {
        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryVector);
        body.put("limit", limit);
        body.put("with_payload", true);

        if (batchRunId != null && !batchRunId.isEmpty()) {
            body.put("filter", Map.of(
                    "must", List.of(
                            Map.of("key", "batchRunId",
                                    "match", Map.of("value", batchRunId))
                    )
            ));
        }

        try {
            String response = webClient.post()
                    .uri("/collections/{name}/points/search", collectionName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("result");

            List<SearchResult> searchResults = new ArrayList<>();
            for (JsonNode result : results) {
                Map<String, Object> payload = new HashMap<>();
                result.path("payload").fields().forEachRemaining(
                        entry -> payload.put(entry.getKey(), entry.getValue().asText()));

                searchResults.add(SearchResult.builder()
                        .id(result.path("id").asText())
                        .score(result.path("score").floatValue())
                        .payload(payload)
                        .build());
            }
            return searchResults;
        } catch (Exception e) {
            log.error("Qdrant search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private String id;
        private float score;
        private Map<String, Object> payload;
    }
}
