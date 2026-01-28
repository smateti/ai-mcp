package com.naagi.rag.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Re-ranking service to improve retrieval precision.
 *
 * Supports multiple backends:
 * - local: Local cross-encoder via OpenAI-compatible API (llama.cpp, vLLM)
 * - cohere: Cohere Rerank API
 * - jina: Jina Reranker API
 * - llm: Use LLM for scoring (slower but flexible)
 *
 * Re-ranking flow:
 * 1. Initial retrieval returns top-N candidates (e.g., 50)
 * 2. Cross-encoder scores each (query, document) pair
 * 3. Return top-K re-ranked results (e.g., 5)
 */
@Service
public class RerankerService {

    private static final Logger log = LoggerFactory.getLogger(RerankerService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final boolean enabled;
    private final String provider;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int candidateCount;
    private final double minScore;

    public RerankerService(
            @Value("${naagi.rag.rerank.enabled:false}") boolean enabled,
            @Value("${naagi.rag.rerank.provider:local}") String provider,
            @Value("${naagi.rag.rerank.base-url:http://localhost:8001}") String baseUrl,
            @Value("${naagi.rag.rerank.model:bge-reranker-base}") String model,
            @Value("${naagi.rag.rerank.api-key:}") String apiKey,
            @Value("${naagi.rag.rerank.candidate-count:50}") int candidateCount,
            @Value("${naagi.rag.rerank.min-score:0.0}") double minScore
    ) {
        this.enabled = enabled;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.candidateCount = candidateCount;
        this.minScore = minScore;

        log.info("[RERANK] Initialized: enabled={}, provider={}, model={}, candidateCount={}",
                enabled, provider, model, candidateCount);
    }

    /**
     * Document with text and optional metadata
     */
    public record Document(
            String id,
            String text,
            double initialScore,
            Map<String, Object> metadata
    ) {}

    /**
     * Re-ranked result with new score
     */
    public record RerankResult(
            String id,
            String text,
            double rerankScore,
            double initialScore,
            int originalRank,
            int newRank,
            Map<String, Object> metadata
    ) {}

    /**
     * Re-rank documents for a given query.
     *
     * @param query The search query
     * @param documents List of documents from initial retrieval
     * @param topK Number of results to return after re-ranking
     * @return Re-ranked documents
     */
    public List<RerankResult> rerank(String query, List<Document> documents, int topK) {
        if (!enabled) {
            log.debug("[RERANK] Disabled, returning original order");
            return convertToResults(documents, topK);
        }

        if (documents.isEmpty()) {
            return List.of();
        }

        long startTime = System.currentTimeMillis();

        try {
            List<RerankResult> results = switch (provider.toLowerCase()) {
                case "cohere" -> rerankWithCohere(query, documents, topK);
                case "jina" -> rerankWithJina(query, documents, topK);
                case "llm" -> rerankWithLLM(query, documents, topK);
                default -> rerankWithLocal(query, documents, topK);
            };

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[RERANK TIMING] {}ms for {} documents -> {} results (provider={})",
                    elapsed, documents.size(), results.size(), provider);

            return results;
        } catch (Exception e) {
            log.error("[RERANK] Failed, returning original order: {}", e.getMessage());
            return convertToResults(documents, topK);
        }
    }

    /**
     * Re-rank using local cross-encoder (OpenAI-compatible API)
     *
     * Expected endpoint: POST /v1/rerank or POST /rerank
     * Request format: { "query": "...", "documents": ["...", "..."], "model": "..." }
     */
    private List<RerankResult> rerankWithLocal(String query, List<Document> documents, int topK) throws Exception {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("query", query);
        requestBody.put("model", model);
        requestBody.put("top_n", topK);

        ArrayNode docsArray = mapper.createArrayNode();
        for (Document doc : documents) {
            docsArray.add(doc.text());
        }
        requestBody.set("documents", docsArray);

        // Try /v1/rerank first, then /rerank
        String[] endpoints = {"/v1/rerank", "/rerank"};
        HttpResponse<String> response = null;

        for (String endpoint : endpoints) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + endpoint))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                        .build();

                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    break;
                }
            } catch (Exception e) {
                log.debug("[RERANK] Endpoint {} failed: {}", endpoint, e.getMessage());
            }
        }

        if (response == null || response.statusCode() != 200) {
            throw new RuntimeException("Local reranker request failed");
        }

        return parseRerankResponse(response.body(), documents, topK);
    }

    /**
     * Re-rank using Cohere Rerank API
     * https://docs.cohere.com/reference/rerank
     */
    private List<RerankResult> rerankWithCohere(String query, List<Document> documents, int topK) throws Exception {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("query", query);
        requestBody.put("model", model.isBlank() ? "rerank-english-v3.0" : model);
        requestBody.put("top_n", topK);
        requestBody.put("return_documents", false);

        ArrayNode docsArray = mapper.createArrayNode();
        for (Document doc : documents) {
            docsArray.add(doc.text());
        }
        requestBody.set("documents", docsArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.cohere.ai/v1/rerank"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Cohere rerank failed: " + response.statusCode() + " - " + response.body());
        }

        return parseCohereResponse(response.body(), documents, topK);
    }

    /**
     * Re-rank using Jina Reranker API
     * https://jina.ai/reranker/
     */
    private List<RerankResult> rerankWithJina(String query, List<Document> documents, int topK) throws Exception {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("query", query);
        requestBody.put("model", model.isBlank() ? "jina-reranker-v2-base-multilingual" : model);
        requestBody.put("top_n", topK);

        ArrayNode docsArray = mapper.createArrayNode();
        for (Document doc : documents) {
            docsArray.add(doc.text());
        }
        requestBody.set("documents", docsArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.jina.ai/v1/rerank"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Jina rerank failed: " + response.statusCode() + " - " + response.body());
        }

        return parseJinaResponse(response.body(), documents, topK);
    }

    /**
     * Re-rank using LLM scoring (slower but flexible)
     *
     * Uses the LLM to score relevance of each document to the query.
     * Best for small candidate sets or when no dedicated reranker is available.
     */
    private List<RerankResult> rerankWithLLM(String query, List<Document> documents, int topK) throws Exception {
        List<ScoredDoc> scoredDocs = new ArrayList<>();

        // Score each document
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            double score = scoreSingleDocumentWithLLM(query, doc.text());
            scoredDocs.add(new ScoredDoc(i, doc, score));
        }

        // Sort by score descending
        scoredDocs.sort((a, b) -> Double.compare(b.score, a.score));

        // Convert to results
        List<RerankResult> results = new ArrayList<>();
        for (int newRank = 0; newRank < Math.min(topK, scoredDocs.size()); newRank++) {
            ScoredDoc sd = scoredDocs.get(newRank);
            if (sd.score >= minScore) {
                results.add(new RerankResult(
                        sd.doc.id(),
                        sd.doc.text(),
                        sd.score,
                        sd.doc.initialScore(),
                        sd.originalRank,
                        newRank,
                        sd.doc.metadata()
                ));
            }
        }

        return results;
    }

    private record ScoredDoc(int originalRank, Document doc, double score) {}

    /**
     * Score a single document using LLM
     */
    private double scoreSingleDocumentWithLLM(String query, String document) {
        try {
            String prompt = """
                    Rate the relevance of the following document to the query on a scale of 0 to 10.
                    Only respond with a single number, nothing else.

                    Query: %s

                    Document: %s

                    Relevance score (0-10):""".formatted(query, truncate(document, 500));

            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("max_tokens", 5);
            requestBody.put("temperature", 0.0);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/completions"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                String text = root.path("choices").path(0).path("text").asText().trim();
                return Double.parseDouble(text) / 10.0; // Normalize to 0-1
            }
        } catch (Exception e) {
            log.warn("[RERANK] LLM scoring failed: {}", e.getMessage());
        }
        return 0.5; // Default score on failure
    }

    /**
     * Parse standard rerank response format
     * { "results": [{ "index": 0, "relevance_score": 0.95 }, ...] }
     */
    private List<RerankResult> parseRerankResponse(String json, List<Document> documents, int topK) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode results = root.has("results") ? root.get("results") : root;

        List<RerankResult> output = new ArrayList<>();
        int newRank = 0;

        for (JsonNode result : results) {
            if (newRank >= topK) break;

            int index = result.path("index").asInt();
            double score = result.has("relevance_score")
                    ? result.path("relevance_score").asDouble()
                    : result.path("score").asDouble();

            if (index < documents.size() && score >= minScore) {
                Document doc = documents.get(index);
                output.add(new RerankResult(
                        doc.id(),
                        doc.text(),
                        score,
                        doc.initialScore(),
                        index,
                        newRank,
                        doc.metadata()
                ));
                newRank++;
            }
        }

        return output;
    }

    /**
     * Parse Cohere response format
     */
    private List<RerankResult> parseCohereResponse(String json, List<Document> documents, int topK) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode results = root.get("results");

        List<RerankResult> output = new ArrayList<>();
        int newRank = 0;

        for (JsonNode result : results) {
            if (newRank >= topK) break;

            int index = result.path("index").asInt();
            double score = result.path("relevance_score").asDouble();

            if (index < documents.size() && score >= minScore) {
                Document doc = documents.get(index);
                output.add(new RerankResult(
                        doc.id(),
                        doc.text(),
                        score,
                        doc.initialScore(),
                        index,
                        newRank,
                        doc.metadata()
                ));
                newRank++;
            }
        }

        return output;
    }

    /**
     * Parse Jina response format
     */
    private List<RerankResult> parseJinaResponse(String json, List<Document> documents, int topK) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode results = root.get("results");

        List<RerankResult> output = new ArrayList<>();
        int newRank = 0;

        for (JsonNode result : results) {
            if (newRank >= topK) break;

            int index = result.path("index").asInt();
            double score = result.path("relevance_score").asDouble();

            if (index < documents.size() && score >= minScore) {
                Document doc = documents.get(index);
                output.add(new RerankResult(
                        doc.id(),
                        doc.text(),
                        score,
                        doc.initialScore(),
                        index,
                        newRank,
                        doc.metadata()
                ));
                newRank++;
            }
        }

        return output;
    }

    /**
     * Convert documents to results without re-ranking (fallback)
     */
    private List<RerankResult> convertToResults(List<Document> documents, int topK) {
        List<RerankResult> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, documents.size()); i++) {
            Document doc = documents.get(i);
            results.add(new RerankResult(
                    doc.id(),
                    doc.text(),
                    doc.initialScore(),
                    doc.initialScore(),
                    i,
                    i,
                    doc.metadata()
            ));
        }
        return results;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getProvider() {
        return provider;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public Map<String, Object> getStats() {
        return Map.of(
                "enabled", enabled,
                "provider", provider,
                "model", model,
                "candidateCount", candidateCount,
                "minScore", minScore
        );
    }
}
