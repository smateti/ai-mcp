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

    public void ensureCollection() {
        try {
            String response = webClient.get()
                    .uri("/collections/{name}", collectionName)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            if ("ok".equals(root.path("status").asText())) {
                log.info("Qdrant collection '{}' already exists", collectionName);
                return;
            }
        } catch (Exception e) {
            log.info("Collection '{}' not found, creating...", collectionName);
        }

        try {
            Map<String, Object> body = Map.of(
                    "vectors", Map.of(
                            "size", 768,
                            "distance", "Cosine"
                    )
            );

            webClient.put()
                    .uri("/collections/{name}", collectionName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            log.info("Created Qdrant collection '{}'", collectionName);
        } catch (Exception e) {
            log.error("Failed to create Qdrant collection: {}", e.getMessage());
            throw new RuntimeException("Failed to create Qdrant collection", e);
        }
    }

    public void upsertPoints(List<QdrantPoint> points) {
        if (points.isEmpty()) return;

        // Batch in groups of 100
        for (int i = 0; i < points.size(); i += 100) {
            List<QdrantPoint> batch = points.subList(i, Math.min(i + 100, points.size()));
            List<Map<String, Object>> pointMaps = batch.stream()
                    .map(p -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", p.getId());
                        m.put("vector", p.getVector());
                        m.put("payload", p.getPayload());
                        return m;
                    }).toList();

            Map<String, Object> body = Map.of("points", pointMaps);

            try {
                webClient.put()
                        .uri("/collections/{name}/points", collectionName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();

                log.debug("Upserted {} points to Qdrant", batch.size());
            } catch (Exception e) {
                log.error("Failed to upsert points to Qdrant: {}", e.getMessage());
                throw new RuntimeException("Qdrant upsert failed", e);
            }
        }
    }

    public List<SearchResult> search(float[] queryVector, int limit, Map<String, Object> filter) {
        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryVector);
        body.put("limit", limit);
        body.put("with_payload", true);
        if (filter != null && !filter.isEmpty()) {
            body.put("filter", filter);
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
    public static class QdrantPoint {
        private String id;
        private float[] vector;
        private Map<String, Object> payload;
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
