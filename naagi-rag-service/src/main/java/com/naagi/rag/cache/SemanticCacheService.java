package com.naagi.rag.cache;

import com.naagi.rag.llm.EmbeddingsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.naagi.rag.http.Http;
import com.naagi.rag.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Semantic cache for RAG query results.
 *
 * Before running the full RAG pipeline, checks if a semantically similar query
 * was recently answered. Uses a dedicated Qdrant collection to store
 * (query_embedding, query_text, answer, timestamp).
 *
 * This avoids redundant LLM calls for paraphrased versions of the same question.
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private final EmbeddingsClient embeddingsClient;
    private final String qdrantBaseUrl;
    private final String collection;
    private final int vectorSize;
    private final boolean enabled;
    private final double similarityThreshold;
    private final long ttlMinutes;

    private volatile boolean collectionEnsured = false;

    public record CachedAnswer(String question, String answer, String categoryId, double similarity) {}

    public SemanticCacheService(
            EmbeddingsClient embeddingsClient,
            @Value("${naagi.rag.qdrant.baseUrl}") String qdrantBaseUrl,
            @Value("${naagi.rag.qdrant.vectorSize:768}") int vectorSize,
            @Value("${naagi.rag.semantic-cache.enabled:false}") boolean enabled,
            @Value("${naagi.rag.semantic-cache.collection:naagi_semantic_cache}") String collection,
            @Value("${naagi.rag.semantic-cache.similarity-threshold:0.92}") double similarityThreshold,
            @Value("${naagi.rag.semantic-cache.ttl-minutes:60}") long ttlMinutes) {
        this.embeddingsClient = embeddingsClient;
        this.qdrantBaseUrl = qdrantBaseUrl;
        this.vectorSize = vectorSize;
        this.enabled = enabled;
        this.collection = collection;
        this.similarityThreshold = similarityThreshold;
        this.ttlMinutes = ttlMinutes;
        log.info("[SEMANTIC-CACHE] Initialized, enabled={}, collection={}, threshold={}, ttl={}min",
                enabled, collection, similarityThreshold, ttlMinutes);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Look up a semantically similar cached answer.
     */
    public Optional<CachedAnswer> lookup(String question, String categoryId) {
        if (!enabled) return Optional.empty();

        try {
            ensureCollection();

            List<Double> queryVec = embeddingsClient.embed(question);

            // Search the cache collection
            var body = Json.MAPPER.createObjectNode();
            body.set("vector", toArray(queryVec));
            body.put("limit", 1);
            body.put("with_payload", true);
            body.put("score_threshold", similarityThreshold);

            // Optional category filter
            if (categoryId != null && !categoryId.isBlank()) {
                var matchAny = Json.MAPPER.createObjectNode();
                matchAny.set("any", Json.MAPPER.createArrayNode().add(categoryId));
                var keyFilter = Json.MAPPER.createObjectNode();
                keyFilter.put("key", "categoryId");
                keyFilter.set("match", matchAny);
                var filter = Json.MAPPER.createObjectNode();
                filter.set("must", Json.MAPPER.createArrayNode().add(keyFilter));
                body.set("filter", filter);
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + collection + "/points/search"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("[SEMANTIC-CACHE] Search failed: HTTP {}", resp.statusCode());
                return Optional.empty();
            }

            JsonNode root = Json.MAPPER.readTree(resp.body());
            JsonNode results = root.get("result");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }

            JsonNode hit = results.get(0);
            double score = hit.get("score").asDouble();
            JsonNode payload = hit.get("payload");

            // Check TTL
            if (payload.has("timestamp")) {
                long cachedAt = payload.get("timestamp").asLong();
                long ageMinutes = (Instant.now().toEpochMilli() - cachedAt) / 60_000;
                if (ageMinutes > ttlMinutes) {
                    log.debug("[SEMANTIC-CACHE] Hit expired (age={}min, ttl={}min)", ageMinutes, ttlMinutes);
                    return Optional.empty();
                }
            }

            String cachedQuestion = payload.has("question") ? payload.get("question").asText() : "";
            String cachedAnswer = payload.has("answer") ? payload.get("answer").asText() : "";
            String cachedCategory = payload.has("categoryId") ? payload.get("categoryId").asText() : "";

            log.info("[SEMANTIC-CACHE] HIT (score={}) for query: {}", String.format("%.4f", score),
                    question.substring(0, Math.min(80, question.length())));

            return Optional.of(new CachedAnswer(cachedQuestion, cachedAnswer, cachedCategory, score));

        } catch (Exception e) {
            log.warn("[SEMANTIC-CACHE] Lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Store a query-answer pair in the semantic cache.
     */
    public void store(String question, String answer, String categoryId) {
        if (!enabled) return;

        try {
            ensureCollection();

            List<Double> queryVec = embeddingsClient.embed(question);
            String pointId = stableId(question + "|" + (categoryId != null ? categoryId : ""));

            Map<String, Object> payload = Map.of(
                    "question", question,
                    "answer", answer,
                    "categoryId", categoryId != null ? categoryId : "",
                    "timestamp", Instant.now().toEpochMilli()
            );

            // Use raw Qdrant API since QdrantClient is bound to main collection
            var arr = Json.MAPPER.createArrayNode();
            var obj = Json.MAPPER.createObjectNode();
            obj.put("id", pointId);
            obj.set("vector", toArray(queryVec));
            obj.set("payload", Json.MAPPER.valueToTree(payload));
            arr.add(obj);

            var body = Json.MAPPER.createObjectNode();
            body.set("points", arr);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + collection + "/points?wait=true"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) {
                log.debug("[SEMANTIC-CACHE] Stored answer for: {}", question.substring(0, Math.min(80, question.length())));
            } else {
                log.warn("[SEMANTIC-CACHE] Store failed: HTTP {}", resp.statusCode());
            }

        } catch (Exception e) {
            log.warn("[SEMANTIC-CACHE] Store failed: {}", e.getMessage());
        }
    }

    private void ensureCollection() {
        if (collectionEnsured) return;
        synchronized (this) {
            if (collectionEnsured) return;
            try {
                HttpRequest getReq = HttpRequest.newBuilder()
                        .uri(URI.create(qdrantBaseUrl + "/collections/" + collection))
                        .timeout(Duration.ofSeconds(10))
                        .GET().build();
                HttpResponse<String> getResp = Http.CLIENT.send(getReq, HttpResponse.BodyHandlers.ofString());
                if (getResp.statusCode() == 200) {
                    collectionEnsured = true;
                    return;
                }

                var vectors = Json.MAPPER.createObjectNode().put("size", vectorSize).put("distance", "Cosine");
                var body = Json.MAPPER.createObjectNode();
                body.set("vectors", vectors);

                HttpRequest putReq = HttpRequest.newBuilder()
                        .uri(URI.create(qdrantBaseUrl + "/collections/" + collection))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                        .build();
                Http.CLIENT.send(putReq, HttpResponse.BodyHandlers.ofString());
                collectionEnsured = true;
                log.info("[SEMANTIC-CACHE] Created collection: {}", collection);
            } catch (Exception e) {
                log.warn("[SEMANTIC-CACHE] Failed to ensure collection: {}", e.getMessage());
            }
        }
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode toArray(List<Double> v) {
        var a = Json.MAPPER.createArrayNode();
        for (Double d : v) a.add(d);
        return a;
    }

    private static String stableId(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
