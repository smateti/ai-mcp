package com.naag.rag.controller;

import com.naag.rag.dto.*;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.metrics.RagMetrics;
import com.naag.rag.service.RagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagController {

    private final RagService ragService;
    private final EmbeddingsClient embeddingsClient;
    private final RagMetrics metrics;
    private final String qdrantBaseUrl;
    private final String qdrantCollection;

    public RagController(
            RagService ragService,
            EmbeddingsClient embeddingsClient,
            RagMetrics metrics,
            @Value("${naag.rag.qdrant.baseUrl}") String qdrantBaseUrl,
            @Value("${naag.rag.qdrant.collection}") String qdrantCollection
    ) {
        this.ragService = ragService;
        this.embeddingsClient = embeddingsClient;
        this.metrics = metrics;
        this.qdrantBaseUrl = qdrantBaseUrl;
        this.qdrantCollection = qdrantCollection;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        try {
            request.validate();

            int chunksCreated = ragService.ingest(request.docId(), request.text(), request.getCategoriesOrEmpty());

            return ResponseEntity.ok(IngestResponse.success(request.docId(), chunksCreated));

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(IngestResponse.error(request.docId(), "Validation error: " + e.getMessage()));

        } catch (Exception e) {
            System.err.println("[RAG API] Ingest failed for docId=" + request.docId() + ": " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(IngestResponse.error(request.docId(), "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        try {
            request.validate();

            int topK = request.getTopKOrDefault();

            RagService.QueryResult result = ragService.askWithSources(request.question(), topK, request.category());

            List<SourceMetadata> sources = result.sources().stream()
                    .map(s -> new SourceMetadata(
                            s.docId(),
                            s.chunkIndex(),
                            s.relevanceScore(),
                            s.text()
                    ))
                    .collect(Collectors.toList());

            QueryResponse response = QueryResponse.success(
                    result.question(),
                    result.answer(),
                    sources
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(QueryResponse.error(request.question(), "Validation error: " + e.getMessage()));

        } catch (Exception e) {
            System.err.println("[RAG API] Query failed for question='" + request.question() + "': " + e.getMessage());
            e.printStackTrace();
            metrics.recordQueryError();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(QueryResponse.error(request.question(), "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Streaming query endpoint using Server-Sent Events (SSE).
     * Streams the response token by token for a better user experience.
     *
     * Event types:
     * - sources: Contains the retrieved source chunks (sent first)
     * - token: Contains a single token of the answer
     * - done: Indicates the stream is complete
     * - error: Contains error information if something goes wrong
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody QueryRequest request) {
        SseEmitter emitter = new SseEmitter(120000L); // 2 minute timeout

        // Run in a separate thread to not block
        new Thread(() -> {
            try {
                request.validate();
                int topK = request.getTopKOrDefault();

                ragService.askWithSourcesStream(request.question(), topK, request.category(), event -> {
                    try {
                        switch (event.type()) {
                            case "sources" -> {
                                // Convert sources to SourceMetadata for consistent API
                                List<SourceMetadata> sources = event.sources().stream()
                                        .map(s -> new SourceMetadata(
                                                s.docId(),
                                                s.chunkIndex(),
                                                s.relevanceScore(),
                                                s.text()
                                        ))
                                        .collect(Collectors.toList());
                                emitter.send(SseEmitter.event()
                                        .name("sources")
                                        .data(new com.fasterxml.jackson.databind.ObjectMapper()
                                                .writeValueAsString(sources)));
                            }
                            case "token" -> {
                                // Send token as JSON to preserve whitespace
                                emitter.send(SseEmitter.event()
                                        .name("token")
                                        .data("{\"t\":\"" + escapeJson(event.token()) + "\"}"));
                            }
                            case "prompt" -> {
                                // Send LLM prompt for audit trail
                                java.util.Map<String, String> promptMap = new java.util.HashMap<>();
                                promptMap.put("prompt", event.prompt() != null ? event.prompt() : "");
                                emitter.send(SseEmitter.event()
                                        .name("prompt")
                                        .data(new com.fasterxml.jackson.databind.ObjectMapper()
                                                .writeValueAsString(promptMap)));
                            }
                            case "done" -> {
                                emitter.send(SseEmitter.event()
                                        .name("done")
                                        .data(""));
                                emitter.complete();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[RAG API] Stream event send failed: " + e.getMessage());
                    }
                });

            } catch (IllegalArgumentException e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Validation error: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } catch (Exception e) {
                System.err.println("[RAG API] Stream query failed: " + e.getMessage());
                e.printStackTrace();
                metrics.recordQueryError();
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Internal server error: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        }).start();

        return emitter;
    }

    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> getStats() {
        try {
            java.util.Map<String, Object> stats = new java.util.HashMap<>();

            // Get collection info from Qdrant
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + qdrantCollection))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = objectMapper.readTree(response.body());
                var result = root.get("result");

                if (result != null) {
                    int vectorCount = result.has("vectors_count") ? result.get("vectors_count").asInt() : 0;
                    int pointsCount = result.has("points_count") ? result.get("points_count").asInt() : vectorCount;

                    stats.put("vectorCount", vectorCount);
                    stats.put("totalChunks", pointsCount);

                    // Get actual document count by scrolling unique docIds
                    int docCount = countUniqueDocuments();
                    stats.put("totalDocuments", docCount);
                }
            } else {
                stats.put("totalDocuments", 0);
                stats.put("totalChunks", 0);
                stats.put("vectorCount", 0);
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            System.err.println("[RAG API] Stats fetch failed: " + e.getMessage());
            return ResponseEntity.ok(java.util.Map.of(
                    "totalDocuments", 0,
                    "totalChunks", 0,
                    "vectorCount", 0
            ));
        }
    }

    private int countUniqueDocuments() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var scrollBody = objectMapper.createObjectNode();
            scrollBody.put("limit", 1000);
            scrollBody.put("with_payload", true);
            scrollBody.put("with_vector", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + qdrantCollection + "/points/scroll"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(scrollBody)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var root = objectMapper.readTree(response.body());
                var result = root.get("result");
                var points = result != null ? result.get("points") : null;

                if (points != null && points.isArray()) {
                    java.util.Set<String> uniqueDocIds = new java.util.HashSet<>();
                    for (var point : points) {
                        var payload = point.get("payload");
                        if (payload != null && payload.has("docId")) {
                            uniqueDocIds.add(payload.get("docId").asText());
                        }
                    }
                    return uniqueDocIds.size();
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("[RAG API] Count documents failed: " + e.getMessage());
            return 0;
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<List<java.util.Map<String, Object>>> getDocuments(
            @RequestParam(required = false) String categoryId) {
        try {
            List<java.util.Map<String, Object>> documents = new java.util.ArrayList<>();

            HttpClient client = HttpClient.newHttpClient();
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var scrollBody = objectMapper.createObjectNode();
            scrollBody.put("limit", 1000);
            scrollBody.put("with_payload", true);
            scrollBody.put("with_vector", false);

            // Add category filter if provided
            if (categoryId != null && !categoryId.isBlank()) {
                var matchAny = objectMapper.createObjectNode();
                matchAny.set("any", objectMapper.createArrayNode().add(categoryId));

                var keyFilter = objectMapper.createObjectNode();
                keyFilter.put("key", "categories");
                keyFilter.set("match", matchAny);

                var filter = objectMapper.createObjectNode();
                filter.set("must", objectMapper.createArrayNode().add(keyFilter));

                scrollBody.set("filter", filter);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + qdrantCollection + "/points/scroll"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(scrollBody)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var root = objectMapper.readTree(response.body());
                var result = root.get("result");
                var points = result != null ? result.get("points") : null;

                if (points != null && points.isArray()) {
                    // Group by docId and count chunks
                    java.util.Map<String, java.util.Map<String, Object>> docMap = new java.util.LinkedHashMap<>();

                    for (var point : points) {
                        var payload = point.get("payload");
                        if (payload != null && payload.has("docId")) {
                            String docId = payload.get("docId").asText();

                            docMap.computeIfAbsent(docId, k -> {
                                java.util.Map<String, Object> doc = new java.util.HashMap<>();
                                doc.put("docId", k);
                                doc.put("chunkCount", 0);
                                if (payload.has("title")) {
                                    doc.put("title", payload.get("title").asText());
                                }
                                return doc;
                            });

                            docMap.get(docId).put("chunkCount",
                                    (Integer) docMap.get(docId).get("chunkCount") + 1);
                        }
                    }

                    documents.addAll(docMap.values());
                }
            }

            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            System.err.println("[RAG API] Documents fetch failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<java.util.Map<String, Object>> deleteDocument(@PathVariable String docId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var deleteBody = objectMapper.createObjectNode();

            var matchFilter = objectMapper.createObjectNode();
            matchFilter.put("key", "docId");
            var matchValue = objectMapper.createObjectNode();
            matchValue.put("value", docId);
            matchFilter.set("match", matchValue);

            var filter = objectMapper.createObjectNode();
            filter.set("must", objectMapper.createArrayNode().add(matchFilter));

            deleteBody.set("filter", filter);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + qdrantCollection + "/points/delete?wait=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(deleteBody)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return ResponseEntity.ok(java.util.Map.of("success", true, "docId", docId));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(java.util.Map.of("success", false, "error", "Failed to delete: " + response.body()));
            }
        } catch (Exception e) {
            System.err.println("[RAG API] Document delete failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        try {
            boolean qdrantConnected = checkQdrantConnection();
            boolean llmProviderConnected = checkLlmProviderConnection();
            boolean collectionExists = checkCollectionExists();

            HealthResponse response = HealthResponse.healthy(
                    qdrantConnected,
                    llmProviderConnected,
                    collectionExists
            );

            HttpStatus status = response.status().equals("healthy")
                    ? HttpStatus.OK
                    : HttpStatus.SERVICE_UNAVAILABLE;

            return ResponseEntity.status(status).body(response);

        } catch (Exception e) {
            System.err.println("[RAG API] Health check failed: " + e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(HealthResponse.unhealthy("Health check error: " + e.getMessage()));
        }
    }

    private boolean checkQdrantConnection() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("[RAG API] Qdrant connection check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean checkLlmProviderConnection() {
        try {
            List<Double> testEmbedding = embeddingsClient.embed("health check");
            return testEmbedding != null && !testEmbedding.isEmpty();
        } catch (Exception e) {
            System.err.println("[RAG API] LLM provider connection check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean checkCollectionExists() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + qdrantCollection))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("[RAG API] Collection exists check failed: " + e.getMessage());
            return false;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
