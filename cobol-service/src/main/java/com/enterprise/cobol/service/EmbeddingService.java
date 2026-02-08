package com.enterprise.cobol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.embedding-model}")
    private String embeddingModel;

    public EmbeddingService(@Value("${llm.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public float[] embedQuery(String text) {
        return embed("search_query: " + text);
    }

    private float[] embed(String text) {
        try {
            Map<String, Object> request = Map.of(
                    "model", embeddingModel,
                    "input", List.of(text)
            );

            String response = webClient.post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode embedding = root.path("data").get(0).path("embedding");

            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (Exception e) {
            log.error("Embedding failed: {}", e.getMessage());
            throw new RuntimeException("Embedding failed", e);
        }
    }
}
