package com.naag.rag.qdrant;

import com.naag.rag.http.Http;
import com.naag.rag.json.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant client for User Questions collection.
 * Used for deduplication and finding similar questions.
 */
public final class UserQuestionQdrantClient {
    private static final Logger log = LoggerFactory.getLogger(UserQuestionQdrantClient.class);

    private final String baseUrl;
    private final String collection;
    private final int vectorSize;

    private volatile boolean ensured = false;

    public UserQuestionQdrantClient(String baseUrl, String collection, int vectorSize) {
        this.baseUrl = baseUrl;
        this.collection = collection;
        this.vectorSize = vectorSize;
    }

    public record QuestionPoint(
            String id,
            List<Double> vector,
            String questionId,
            String question,
            String categoryId,
            String categoryName,
            String sourceDocId,
            int frequency,
            String matchedFaqId,
            LocalDateTime askedAt
    ) {}

    public record SimilarQuestionResult(
            String questionId,
            String question,
            String categoryId,
            String categoryName,
            int frequency,
            String matchedFaqId,
            double score
    ) {}

    public void ensureCollectionExists() {
        if (ensured) return;
        synchronized (this) {
            if (ensured) return;

            try {
                HttpRequest getReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/collections/" + collection))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> getResp = Http.CLIENT.send(getReq, HttpResponse.BodyHandlers.ofString());

                if (getResp.statusCode() == 200) {
                    ensured = true;
                    log.info("User questions collection '{}' exists", collection);
                    return;
                }
                if (getResp.statusCode() != 404) {
                    throw new RuntimeException("Qdrant GET collection HTTP " + getResp.statusCode() + ": " + getResp.body());
                }

                // Create collection
                ObjectNode vectors = Json.MAPPER.createObjectNode()
                        .put("size", vectorSize)
                        .put("distance", "Cosine");

                ObjectNode body = Json.MAPPER.createObjectNode();
                body.set("vectors", vectors);

                HttpRequest putReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/collections/" + collection))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                        .build();

                HttpResponse<String> putResp = Http.CLIENT.send(putReq, HttpResponse.BodyHandlers.ofString());
                if (putResp.statusCode() / 100 != 2) {
                    throw new RuntimeException("Qdrant CREATE collection HTTP " + putResp.statusCode() + ": " + putResp.body());
                }

                log.info("Created user questions collection '{}'", collection);
                ensured = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to ensure user questions Qdrant collection exists", e);
            }
        }
    }

    /**
     * Upsert a question point
     */
    public void upsertQuestion(QuestionPoint point) {
        upsertQuestions(List.of(point));
    }

    /**
     * Upsert multiple question points
     */
    public void upsertQuestions(List<QuestionPoint> points) {
        ensureCollectionExists();

        try {
            ArrayNode arr = Json.MAPPER.createArrayNode();
            for (QuestionPoint p : points) {
                if (p.vector() == null || p.vector().size() != vectorSize) {
                    throw new IllegalArgumentException("Vector dimension mismatch for questionId=" + p.questionId()
                            + " expected=" + vectorSize
                            + " got=" + (p.vector() == null ? "null" : p.vector().size()));
                }

                ObjectNode obj = Json.MAPPER.createObjectNode();
                obj.put("id", p.id());
                obj.set("vector", toArray(p.vector()));

                ObjectNode payload = Json.MAPPER.createObjectNode();
                payload.put("questionId", p.questionId());
                payload.put("question", p.question());
                if (p.categoryId() != null) payload.put("categoryId", p.categoryId());
                if (p.categoryName() != null) payload.put("categoryName", p.categoryName());
                if (p.sourceDocId() != null) payload.put("sourceDocId", p.sourceDocId());
                payload.put("frequency", p.frequency());
                if (p.matchedFaqId() != null) payload.put("matchedFaqId", p.matchedFaqId());
                if (p.askedAt() != null) payload.put("askedAt", p.askedAt().toString());

                obj.set("payload", payload);
                arr.add(obj);
            }

            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("points", arr);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collection + "/points?wait=true"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Qdrant user question upsert HTTP " + resp.statusCode() + ": " + resp.body());
            }

            log.debug("Upserted {} user question points to collection '{}'", points.size(), collection);
        } catch (Exception e) {
            throw new RuntimeException("Qdrant user question upsert failed", e);
        }
    }

    /**
     * Find similar questions (for deduplication)
     * @param queryVector embedding of the question
     * @param topK max results
     * @param minScore minimum similarity score (e.g., 0.95 for deduplication)
     * @return list of similar questions
     */
    public List<SimilarQuestionResult> findSimilarQuestions(List<Double> queryVector, int topK, double minScore) {
        return findSimilarQuestions(queryVector, topK, minScore, null);
    }

    /**
     * Find similar questions with optional category filter
     */
    public List<SimilarQuestionResult> findSimilarQuestions(List<Double> queryVector, int topK, double minScore, String categoryFilter) {
        ensureCollectionExists();

        try {
            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("vector", toArray(queryVector));
            body.put("limit", topK);
            body.put("with_payload", true);
            body.put("score_threshold", minScore);

            if (categoryFilter != null && !categoryFilter.isBlank()) {
                ObjectNode matchVal = Json.MAPPER.createObjectNode();
                matchVal.put("value", categoryFilter);

                ObjectNode keyFilter = Json.MAPPER.createObjectNode();
                keyFilter.put("key", "categoryId");
                keyFilter.set("match", matchVal);

                ObjectNode filter = Json.MAPPER.createObjectNode();
                filter.set("must", Json.MAPPER.createArrayNode().add(keyFilter));

                body.set("filter", filter);
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collection + "/points/search"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Qdrant similar questions search HTTP " + resp.statusCode() + ": " + resp.body());
            }

            return parseResults(resp.body());
        } catch (Exception e) {
            throw new RuntimeException("Qdrant similar questions search failed", e);
        }
    }

    /**
     * Update frequency in Qdrant payload
     */
    public void updateFrequency(String pointId, int newFrequency) {
        ensureCollectionExists();

        try {
            ObjectNode payload = Json.MAPPER.createObjectNode();
            payload.put("frequency", newFrequency);

            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("payload", payload);

            ObjectNode pointsSelector = Json.MAPPER.createObjectNode();
            pointsSelector.set("points", Json.MAPPER.createArrayNode().add(pointId));
            body.set("points", Json.MAPPER.createArrayNode().add(pointId));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collection + "/points/payload?wait=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Failed to update frequency in Qdrant: HTTP {}", resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to update frequency in Qdrant", e);
        }
    }

    /**
     * Delete a question point
     */
    public void deleteQuestion(String pointId) {
        ensureCollectionExists();

        try {
            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("points", Json.MAPPER.createArrayNode().add(pointId));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collection + "/points/delete?wait=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Qdrant delete HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Qdrant delete failed", e);
        }
    }

    /**
     * Get collection statistics
     */
    public Map<String, Object> getCollectionStats() {
        ensureCollectionExists();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collection))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Qdrant stats HTTP " + resp.statusCode() + ": " + resp.body());
            }

            JsonNode root = Json.MAPPER.readTree(resp.body());
            JsonNode result = root.get("result");
            if (result == null) {
                return Map.of();
            }

            long pointsCount = result.path("points_count").asLong(0);
            String status = result.path("status").asText("unknown");

            return Map.of(
                    "collection", collection,
                    "pointsCount", pointsCount,
                    "status", status
            );
        } catch (Exception e) {
            log.error("Failed to get user questions collection stats", e);
            return Map.of("error", e.getMessage());
        }
    }

    private static ArrayNode toArray(List<Double> v) {
        ArrayNode a = Json.MAPPER.createArrayNode();
        for (Double d : v) a.add(d);
        return a;
    }

    private static List<SimilarQuestionResult> parseResults(String json) {
        try {
            JsonNode root = Json.MAPPER.readTree(json);
            JsonNode result = root.get("result");
            if (result == null || !result.isArray()) return List.of();

            List<SimilarQuestionResult> out = new ArrayList<>();
            for (JsonNode hit : result) {
                JsonNode payload = hit.get("payload");
                if (payload == null) continue;

                JsonNode scoreNode = hit.get("score");
                double score = scoreNode != null ? scoreNode.asDouble() : 0.0;

                out.add(new SimilarQuestionResult(
                        getTextOrNull(payload, "questionId"),
                        getTextOrNull(payload, "question"),
                        getTextOrNull(payload, "categoryId"),
                        getTextOrNull(payload, "categoryName"),
                        payload.path("frequency").asInt(1),
                        getTextOrNull(payload, "matchedFaqId"),
                        score
                ));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Bad Qdrant search JSON", e);
        }
    }

    private static String getTextOrNull(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return f != null && f.isTextual() ? f.asText() : null;
    }
}
