package com.naagi.rag.controller;

import com.naagi.rag.crag.CragService;
import com.naagi.rag.crag.CragService.CragQueryResult;
import com.naagi.rag.crag.RetrievalEvaluator;
import com.naagi.rag.crag.RetrievalEvaluator.ConfidenceCategory;
import com.naagi.rag.crag.RetrievalEvaluator.EvaluationResult;
import com.naagi.rag.dto.*;
import com.naagi.rag.llm.EmbeddingsClient;
import com.naagi.rag.metrics.RagMetrics;
import com.naagi.rag.service.RagService;
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
    private final CragService cragService;
    private final RetrievalEvaluator retrievalEvaluator;
    private final EmbeddingsClient embeddingsClient;
    private final RagMetrics metrics;
    private final String qdrantBaseUrl;
    private final String qdrantCollection;
    private final double minRelevanceForAnswer;

    public RagController(
            RagService ragService,
            CragService cragService,
            RetrievalEvaluator retrievalEvaluator,
            EmbeddingsClient embeddingsClient,
            RagMetrics metrics,
            @Value("${naagi.rag.qdrant.baseUrl}") String qdrantBaseUrl,
            @Value("${naagi.rag.qdrant.collection}") String qdrantCollection,
            @Value("${naagi.rag.crag.min-relevance-for-answer:0.7}") double minRelevanceForAnswer
    ) {
        this.ragService = ragService;
        this.cragService = cragService;
        this.retrievalEvaluator = retrievalEvaluator;
        this.embeddingsClient = embeddingsClient;
        this.metrics = metrics;
        this.qdrantBaseUrl = qdrantBaseUrl;
        this.qdrantCollection = qdrantCollection;
        this.minRelevanceForAnswer = minRelevanceForAnswer;
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
    public ResponseEntity<QueryResponse> query(
            @RequestBody QueryRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean rerank,
            @RequestParam(required = false, defaultValue = "true") boolean crag) {
        try {
            request.validate();

            int topK = request.getTopKOrDefault();

            // Use CRAG if enabled (default true to prevent hallucinations)
            if (crag && cragService.isEnabled()) {
                CragQueryResult cragResult = cragService.askWithCrag(
                        request.question(), topK, request.category());

                List<SourceMetadata> sources = cragResult.sources().stream()
                        .map(s -> new SourceMetadata(
                                s.docId(),
                                s.chunkIndex(),
                                s.relevanceScore(),
                                s.text(),
                                s.title()
                        ))
                        .collect(Collectors.toList());

                return ResponseEntity.ok(QueryResponse.success(
                        cragResult.question(),
                        cragResult.answer(),
                        sources
                ));
            }

            // Fallback: Use reranking if enabled and requested
            RagService.QueryResult result = rerank && ragService.isRerankerEnabled()
                    ? ragService.askWithReranking(request.question(), topK, request.category())
                    : ragService.askWithSources(request.question(), topK, request.category());

            List<SourceMetadata> sources = result.sources().stream()
                    .map(s -> new SourceMetadata(
                            s.docId(),
                            s.chunkIndex(),
                            s.relevanceScore(),
                            s.text(),
                            s.title()
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
     * CRAG (Corrective RAG) query endpoint.
     * Evaluates retrieval confidence and applies corrective strategies:
     * - CORRECT: Uses retrieved documents directly
     * - AMBIGUOUS: Refines context, adds uncertainty markers
     * - INCORRECT: Expands query, merges sources, adds disclaimers
     */
    @PostMapping("/query/crag")
    public ResponseEntity<CragQueryResponse> queryCrag(
            @RequestBody QueryRequest request) {
        try {
            request.validate();
            int topK = request.getTopKOrDefault();

            CragQueryResult result = cragService.askWithCrag(
                    request.question(), topK, request.category());

            List<SourceMetadata> sources = result.sources().stream()
                    .map(s -> new SourceMetadata(
                            s.docId(),
                            s.chunkIndex(),
                            s.relevanceScore(),
                            s.text(),
                            s.title()
                    ))
                    .collect(Collectors.toList());

            CragQueryResponse response = new CragQueryResponse(
                    true,
                    result.question(),
                    result.answer(),
                    sources,
                    null,
                    result.cragMetadata().confidenceScore(),
                    result.cragMetadata().category().name(),
                    result.cragMetadata().evaluationReason(),
                    result.cragMetadata().appliedStrategies(),
                    result.cragMetadata().retriesPerformed(),
                    result.cragMetadata().expandedQueries()
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(CragQueryResponse.error(request.question(), "Validation error: " + e.getMessage()));

        } catch (Exception e) {
            System.err.println("[RAG API] CRAG query failed for question='" + request.question() + "': " + e.getMessage());
            e.printStackTrace();
            metrics.recordQueryError();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CragQueryResponse.error(request.question(), "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Streaming query endpoint using Server-Sent Events (SSE).
     * Streams the response token by token for a better user experience.
     *
     * Now includes CRAG (Corrective RAG) protection:
     * - Evaluates retrieval confidence before streaming
     * - Refuses to generate answer if relevance is too low (prevents hallucination)
     *
     * Event types:
     * - sources: Contains the retrieved source chunks (sent first)
     * - token: Contains a single token of the answer
     * - done: Indicates the stream is complete
     * - error: Contains error information if something goes wrong
     * - crag: Contains CRAG metadata (confidence, category)
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody QueryRequest request) {
        SseEmitter emitter = new SseEmitter(120000L); // 2 minute timeout

        // Run in a separate thread to not block
        new Thread(() -> {
            try {
                request.validate();
                int topK = request.getTopKOrDefault();

                // CRAG: First, get sources and evaluate confidence before streaming
                List<RagService.SourceChunk> sources = ragService.searchWithReranking(
                        request.question(), topK, request.category());

                EvaluationResult evaluation = retrievalEvaluator.evaluate(request.question(), sources);
                double topRelevanceScore = sources.isEmpty() ? 0.0 : sources.get(0).relevanceScore();

                System.out.println("[RAG API] CRAG stream evaluation: confidence=" +
                        String.format("%.3f", evaluation.confidenceScore()) +
                        ", category=" + evaluation.category() +
                        ", topScore=" + String.format("%.3f", topRelevanceScore));

                // Send sources first
                List<SourceMetadata> sourceMetadataList = sources.stream()
                        .map(s -> new SourceMetadata(s.docId(), s.chunkIndex(), s.relevanceScore(), s.text(), s.title()))
                        .collect(Collectors.toList());
                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(sourceMetadataList)));

                // Send CRAG metadata
                java.util.Map<String, Object> cragMetadata = new java.util.LinkedHashMap<>();
                cragMetadata.put("confidenceScore", evaluation.confidenceScore());
                cragMetadata.put("category", evaluation.category().name());
                cragMetadata.put("topRelevanceScore", topRelevanceScore);
                emitter.send(SseEmitter.event()
                        .name("crag")
                        .data(new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(cragMetadata)));

                // CRAG: Check if we should refuse to answer
                // 1. Check keyword overlap - if key terms from question aren't in context, refuse
                String contextText = sources.stream()
                        .map(RagService.SourceChunk::text)
                        .collect(java.util.stream.Collectors.joining(" ")).toLowerCase();

                System.out.println("[RAG API] === ANTI-HALLUCINATION CHECK v2 ===");
                System.out.println("[RAG API] Question: " + request.question());
                System.out.println("[RAG API] CRAG enabled: " + cragService.isEnabled());
                System.out.println("[RAG API] Context length: " + contextText.length() + " chars");

                boolean hasKeywordOverlap = checkKeywordOverlap(request.question(), contextText);

                System.out.println("[RAG API] Keyword overlap result: " + hasKeywordOverlap);

                boolean shouldRefuse = cragService.isEnabled() && (
                        // Original check: low relevance score
                        (topRelevanceScore < minRelevanceForAnswer && evaluation.category() != ConfidenceCategory.CORRECT) ||
                        // NEW: Even for CORRECT, refuse if key question terms aren't in context
                        !hasKeywordOverlap
                );

                if (shouldRefuse) {
                    String refuseReason = !hasKeywordOverlap
                            ? "Key terms from question not found in context"
                            : "Low relevance score";
                    System.out.println("[RAG API] CRAG refusing stream answer: " + refuseReason +
                            ", topScore=" + String.format("%.3f", topRelevanceScore) +
                            ", keywordOverlap=" + hasKeywordOverlap);

                    // Stream refusal message token by token for consistent UX
                    String refusalMessage = "I don't have specific information about that in the knowledge base. " +
                            "The retrieved documents discuss related topics but don't directly address your question.";

                    // Stream the refusal message word by word
                    String[] words = refusalMessage.split(" ");
                    for (int i = 0; i < words.length; i++) {
                        String token = (i == 0 ? "" : " ") + words[i];
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data("{\"t\":\"" + escapeJson(token) + "\"}"));
                        Thread.sleep(20); // Small delay for natural streaming feel
                    }

                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(""));
                    emitter.complete();
                    return;
                }

                // CRAG passed - proceed with normal streaming
                ragService.askWithSourcesStreamFromSources(request.question(), sources, evaluation.category(), event -> {
                    try {
                        switch (event.type()) {
                            case "token" -> {
                                emitter.send(SseEmitter.event()
                                        .name("token")
                                        .data("{\"t\":\"" + escapeJson(event.token()) + "\"}"));
                            }
                            case "prompt" -> {
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

            // Add reranker and hybrid search status
            stats.put("rerankerEnabled", ragService.isRerankerEnabled());
            stats.put("rerankerStats", ragService.getRerankerStats());
            stats.put("hybridSearchEnabled", ragService.isHybridSearchEnabled());
            stats.put("bm25Stats", ragService.getBM25Stats());

            // Add CRAG status
            stats.put("cragEnabled", cragService.isEnabled());
            stats.put("cragStats", cragService.getStats());

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

    @GetMapping("/default-prompt-template")
    public ResponseEntity<java.util.Map<String, String>> getDefaultPromptTemplate() {
        return ResponseEntity.ok(java.util.Map.of("template", RagService.DEFAULT_PROMPT_TEMPLATE));
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
                                // Include categories from the payload
                                if (payload.has("categories") && payload.get("categories").isArray()) {
                                    var categories = new java.util.ArrayList<String>();
                                    for (var cat : payload.get("categories")) {
                                        categories.add(cat.asText());
                                    }
                                    doc.put("categories", categories);
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

    @GetMapping("/documents/{docId}")
    public ResponseEntity<java.util.Map<String, Object>> getDocumentInfo(@PathVariable String docId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

            // Filter by docId
            var scrollBody = objectMapper.createObjectNode();
            scrollBody.put("limit", 100);
            scrollBody.put("with_payload", true);
            scrollBody.put("with_vector", false);

            var matchFilter = objectMapper.createObjectNode();
            matchFilter.put("key", "docId");
            var matchValue = objectMapper.createObjectNode();
            matchValue.put("value", docId);
            matchFilter.set("match", matchValue);

            var filter = objectMapper.createObjectNode();
            filter.set("must", objectMapper.createArrayNode().add(matchFilter));
            scrollBody.set("filter", filter);

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

                if (points != null && points.isArray() && points.size() > 0) {
                    java.util.Map<String, Object> docInfo = new java.util.HashMap<>();
                    docInfo.put("docId", docId);
                    docInfo.put("chunkCount", points.size());

                    // Get metadata from first chunk
                    var firstPoint = points.get(0);
                    var payload = firstPoint.get("payload");
                    if (payload != null) {
                        if (payload.has("title")) {
                            docInfo.put("title", payload.get("title").asText());
                        }
                        if (payload.has("systemPrompt")) {
                            docInfo.put("systemPrompt", payload.get("systemPrompt").asText());
                        }
                        if (payload.has("categories")) {
                            var categories = new java.util.ArrayList<String>();
                            for (var cat : payload.get("categories")) {
                                categories.add(cat.asText());
                            }
                            docInfo.put("categories", categories);
                        }
                    }

                    // Get chunk texts
                    var chunks = new java.util.ArrayList<java.util.Map<String, Object>>();
                    for (var point : points) {
                        var chunkPayload = point.get("payload");
                        if (chunkPayload != null) {
                            java.util.Map<String, Object> chunk = new java.util.HashMap<>();
                            chunk.put("chunkIndex", chunkPayload.has("chunkIndex") ? chunkPayload.get("chunkIndex").asInt() : 0);
                            chunk.put("text", chunkPayload.has("text") ? chunkPayload.get("text").asText() : "");
                            chunks.add(chunk);
                        }
                    }
                    // Sort by chunkIndex
                    chunks.sort((a, b) -> Integer.compare((Integer) a.get("chunkIndex"), (Integer) b.get("chunkIndex")));
                    docInfo.put("chunks", chunks);

                    return ResponseEntity.ok(docInfo);
                }
            }

            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            System.err.println("[RAG API] Document info fetch failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/documents")
    public ResponseEntity<java.util.Map<String, Object>> deleteAllDocuments() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

            // Delete all points by using an empty filter
            var deleteBody = objectMapper.createObjectNode();
            var filter = objectMapper.createObjectNode();
            deleteBody.set("filter", filter);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + qdrantCollection + "/points/delete?wait=true"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(deleteBody)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return ResponseEntity.ok(java.util.Map.of("success", true, "message", "All documents deleted"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(java.util.Map.of("success", false, "error", "Failed to delete all: " + response.body()));
            }
        } catch (Exception e) {
            System.err.println("[RAG API] Delete all documents failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("success", false, "error", e.getMessage()));
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

    /**
     * Check if key terms from the question appear in the context.
     * This helps detect when the context is about a related topic but doesn't actually
     * contain information to answer the specific question.
     *
     * CRITICAL: Technical/specific terms (like "async", "synchronous", "api", "rest")
     * must ALL be found in context. These are the terms that make the question specific.
     *
     * For example: Question about "Nimbus async function call" but context only has
     * generic "Spring Batch" info - the critical term "async" won't be found.
     */
    private boolean checkKeywordOverlap(String question, String contextLower) {
        // Common words to exclude
        java.util.Set<String> stopWords = java.util.Set.of(
                "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could",
                "should", "may", "might", "must", "shall", "can", "need", "dare",
                "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
                "into", "through", "during", "before", "after", "above", "below",
                "between", "under", "again", "further", "then", "once", "here",
                "there", "when", "where", "why", "how", "all", "each", "few",
                "more", "most", "other", "some", "such", "no", "nor", "not",
                "only", "own", "same", "so", "than", "too", "very", "just",
                "and", "but", "if", "or", "because", "until", "while", "what",
                "which", "who", "whom", "this", "that", "these", "those", "i",
                "me", "my", "myself", "we", "our", "ours", "you", "your", "it",
                "call", "calling", "use", "using", "get", "set", "create", "make"
        );

        // Technical/specific terms that MUST be found in context if present in question
        // These are the terms that make a question specific vs generic
        java.util.Set<String> criticalTechnicalTerms = java.util.Set.of(
                "async", "asynchronous", "synchronous", "sync",
                "api", "rest", "soap", "grpc", "graphql",
                "stream", "streaming", "batch", "realtime", "real-time",
                "parallel", "concurrent", "thread", "multithreaded",
                "callback", "promise", "future", "await",
                "queue", "kafka", "rabbitmq", "jms", "mq",
                "cache", "redis", "memcache",
                "database", "sql", "nosql", "mongodb", "postgres",
                "transaction", "rollback", "commit",
                "authentication", "authorization", "oauth", "jwt", "token",
                "encrypt", "decrypt", "ssl", "tls", "https",
                "deploy", "kubernetes", "docker", "container",
                "schedule", "cron", "timer", "trigger",
                "error", "exception", "retry", "timeout", "fallback"
        );

        String questionLower = question.toLowerCase();
        String[] words = questionLower.split("\\W+");

        java.util.List<String> allKeyTerms = new java.util.ArrayList<>();
        java.util.List<String> criticalTermsInQuestion = new java.util.ArrayList<>();

        for (String word : words) {
            if (word.length() < 3 || word.matches("\\d+")) {
                continue;
            }

            // Check if this is a critical technical term
            if (criticalTechnicalTerms.contains(word)) {
                criticalTermsInQuestion.add(word);
                allKeyTerms.add(word);
            } else if (!stopWords.contains(word)) {
                allKeyTerms.add(word);
            }
        }

        // Also check for compound critical terms
        if (questionLower.contains("async") && !criticalTermsInQuestion.contains("async")) {
            criticalTermsInQuestion.add("async");
        }
        if (questionLower.contains("synchronous") && !criticalTermsInQuestion.contains("synchronous")) {
            criticalTermsInQuestion.add("synchronous");
        }

        System.out.println("[RAG API] Keyword analysis: allKeyTerms=" + allKeyTerms +
                ", criticalTerms=" + criticalTermsInQuestion);

        // STRICT CHECK: ALL critical technical terms MUST be found in context
        if (!criticalTermsInQuestion.isEmpty()) {
            java.util.List<String> missingCriticalTerms = new java.util.ArrayList<>();
            for (String term : criticalTermsInQuestion) {
                // Check for the term or common variants
                boolean found = contextLower.contains(term);

                // Also check synonyms for async
                if (term.equals("async") && !found) {
                    found = contextLower.contains("asynchronous") ||
                            contextLower.contains("asynchronously");
                }
                if (term.equals("synchronous") && !found) {
                    found = contextLower.contains("synchronously") ||
                            contextLower.contains("sync ");
                }

                if (!found) {
                    missingCriticalTerms.add(term);
                }
            }

            if (!missingCriticalTerms.isEmpty()) {
                System.out.println("[RAG API] CRITICAL TERMS MISSING: " + missingCriticalTerms +
                        " - Context does NOT contain information about these specific topics. REFUSING to answer.");
                return false;
            }

            System.out.println("[RAG API] All critical terms found in context.");
            return true;
        }

        // If no critical terms, fall back to general overlap check
        if (allKeyTerms.isEmpty()) {
            return true;
        }

        int foundCount = 0;
        for (String term : allKeyTerms) {
            if (contextLower.contains(term)) {
                foundCount++;
            }
        }

        double overlapRatio = (double) foundCount / allKeyTerms.size();
        System.out.println("[RAG API] General keyword overlap: found=" + foundCount + "/" + allKeyTerms.size() +
                ", ratio=" + String.format("%.2f", overlapRatio));

        return overlapRatio >= 0.5;
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
