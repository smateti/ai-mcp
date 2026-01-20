package com.example.categoryadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Service for ingesting documents into the RAG system.
 * Calls the RAG API to ingest documents with category metadata.
 */
@Service
@Slf4j
public class RagIngestionService {

    private final String ragServiceUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RagIngestionService(@Value("${rag.service.url:http://localhost:8080}") String ragServiceUrl) {
        this.ragServiceUrl = ragServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("RAG service configured at: {}", ragServiceUrl);
    }

    /**
     * Ingest a document into the RAG system with category association.
     *
     * @param docId      Unique document identifier
     * @param text       Document content to ingest
     * @param categories List of category IDs to associate with this document
     * @return IngestResult containing success status and chunk count
     */
    public IngestResult ingestDocument(String docId, String text, List<String> categories) {
        try {
            log.info("Ingesting document {} with categories {} to RAG service at {}",
                    docId, categories, ragServiceUrl);

            // Build request body
            ObjectNode body = objectMapper.createObjectNode();
            body.put("docId", docId);
            body.put("text", text);

            if (categories != null && !categories.isEmpty()) {
                ArrayNode categoriesArray = body.putArray("categories");
                for (String cat : categories) {
                    categoriesArray.add(cat);
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ragServiceUrl + "/api/rag/ingest"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120)) // Long timeout for large documents
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.error("RAG ingest failed with status {}: {}", response.statusCode(), response.body());
                return new IngestResult(false, 0, "RAG service error: " + response.body());
            }

            // Parse response to get chunk count
            JsonNode responseJson = objectMapper.readTree(response.body());
            boolean success = responseJson.path("success").asBoolean(false);
            int chunksCreated = responseJson.path("chunksCreated").asInt(0);
            String errorMessage = responseJson.path("errorMessage").asText(null);

            if (!success) {
                return new IngestResult(false, 0, errorMessage != null ? errorMessage : "Unknown error");
            }

            log.info("Successfully ingested document {} - {} chunks created", docId, chunksCreated);
            return new IngestResult(true, chunksCreated, null);

        } catch (Exception e) {
            log.error("Failed to ingest document {}", docId, e);
            return new IngestResult(false, 0, "Connection error: " + e.getMessage());
        }
    }

    /**
     * Check if the RAG service is available.
     */
    public boolean isRagServiceAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ragServiceUrl + "/api/rag/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("RAG service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Result of document ingestion.
     */
    public record IngestResult(
            boolean success,
            int chunksCreated,
            String errorMessage
    ) {}
}
