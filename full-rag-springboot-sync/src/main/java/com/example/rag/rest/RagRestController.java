package com.example.rag.rest;

import com.example.rag.llm.EmbeddingsClient;
import com.example.rag.rest.dto.*;
import com.example.rag.service.RagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API controller for RAG (Retrieval Augmented Generation) operations.
 * Provides endpoints for document ingestion, querying, and health checks.
 * <p>
 * This controller exposes the RAG functionality via standard REST API,
 * allowing integration with MCP server and other external systems.
 */
@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagRestController {

    private final RagService ragService;
    private final EmbeddingsClient embeddingsClient;
    private final String qdrantBaseUrl;
    private final String qdrantCollection;

    public RagRestController(
            RagService ragService,
            EmbeddingsClient embeddingsClient,
            @Value("${rag.qdrant.baseUrl}") String qdrantBaseUrl,
            @Value("${rag.qdrant.collection}") String qdrantCollection
    ) {
        this.ragService = ragService;
        this.embeddingsClient = embeddingsClient;
        this.qdrantBaseUrl = qdrantBaseUrl;
        this.qdrantCollection = qdrantCollection;
    }

    /**
     * Ingest a document into the RAG system.
     * The document will be chunked, embedded, and stored in the vector database.
     *
     * @param request the ingestion request containing docId and text
     * @return IngestResponse with success status and chunk count
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        try {
            // Validate request
            request.validate();

            // Ingest the document
            int chunksCreated = ragService.ingest(request.docId(), request.text());

            // Return success response
            return ResponseEntity.ok(IngestResponse.success(request.docId(), chunksCreated));

        } catch (IllegalArgumentException e) {
            // Validation error
            return ResponseEntity
                    .badRequest()
                    .body(IngestResponse.error(request.docId(), "Validation error: " + e.getMessage()));

        } catch (Exception e) {
            // Internal server error
            System.err.println("[RAG API] Ingest failed for docId=" + request.docId() + ": " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(IngestResponse.error(request.docId(), "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Query the RAG system with a question.
     * Searches for relevant document chunks and generates an answer using LLM.
     *
     * @param request the query request containing the question and optional topK
     * @return QueryResponse with answer and source citations
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        try {
            // Validate request
            request.validate();

            // Get topK value (with default)
            int topK = request.getTopKOrDefault();

            // Execute query with sources
            RagService.QueryResult result = ragService.askWithSources(request.question(), topK);

            // Convert to DTO
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
            // Validation error
            return ResponseEntity
                    .badRequest()
                    .body(QueryResponse.error(request.question(), "Validation error: " + e.getMessage()));

        } catch (Exception e) {
            // Internal server error
            System.err.println("[RAG API] Query failed for question='" + request.question() + "': " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(QueryResponse.error(request.question(), "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint to verify RAG system components are working.
     * Checks connectivity to Qdrant and LLM provider.
     *
     * @return HealthResponse with status of all components
     */
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

            // Return 200 if all healthy, 503 if any component is down
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

    /**
     * Check if Qdrant is accessible.
     */
    private boolean checkQdrantConnection() {
        try {
            // Try to make a simple HTTP request to Qdrant
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(qdrantBaseUrl + "/collections"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );

            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("[RAG API] Qdrant connection check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if LLM provider (Ollama/llama.cpp) is accessible.
     */
    private boolean checkLlmProviderConnection() {
        try {
            // Try to generate a test embedding
            List<Double> testEmbedding = embeddingsClient.embed("health check");
            return testEmbedding != null && !testEmbedding.isEmpty();
        } catch (Exception e) {
            System.err.println("[RAG API] LLM provider connection check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if the Qdrant collection exists.
     */
    private boolean checkCollectionExists() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(qdrantBaseUrl + "/collections/" + qdrantCollection))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );

            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("[RAG API] Collection exists check failed: " + e.getMessage());
            return false;
        }
    }
}
