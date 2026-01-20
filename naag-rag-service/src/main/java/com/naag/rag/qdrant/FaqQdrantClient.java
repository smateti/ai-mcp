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
 * Qdrant client for FAQ collection.
 * Stores FAQ questions as embeddings for semantic search.
 */
public final class FaqQdrantClient {
    private static final Logger log = LoggerFactory.getLogger(FaqQdrantClient.class);

    private final String baseUrl;
    private final String collection;
    private final int vectorSize;

    private volatile boolean ensured = false;

    public FaqQdrantClient(String baseUrl, String collection, int vectorSize) {
        this.baseUrl = baseUrl;
        this.collection = collection;
        this.vectorSize = vectorSize;
    }

    public record FaqPoint(
            String id,
            List<Double> vector,
            String faqId,
            String question,
            String answer,
            String categoryId,
            String categoryName,
            String docId,
            String docTitle,
            String uploadId,
            String questionType,
            String approvedBy,
            LocalDateTime createdAt
    ) {}

    public record FaqSearchResult(
            String faqId,
            String question,
            String answer,
            String categoryId,
            String categoryName,
            String docId,
            String docTitle,
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
                    log.info("FAQ collection '{}' exists", collection);
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

                log.info("Created FAQ collection '{}'", collection);
                ensured = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to ensure FAQ Qdrant collection exists", e);
            }
        }
    }

    /**
     * Upsert a single FAQ point
     */
    public void upsertFaq(FaqPoint point) {
        upsertFaqs(List.of(point));
    }

    /**
     * Upsert multiple FAQ points
     */
    public void upsertFaqs(List<FaqPoint> points) {
        ensureCollectionExists();

        try {
            ArrayNode arr = Json.MAPPER.createArrayNode();
            for (FaqPoint p : points) {
                if (p.vector() == null || p.vector().size() != vectorSize) {
                    throw new IllegalArgumentException("Vector dimension mismatch for faqId=" + p.faqId()
                            + " expected=" + vectorSize
                            + " got=" + (p.vector() == null ? "null" : p.vector().size()));
                }

                ObjectNode obj = Json.MAPPER.createObjectNode();
                obj.put("id", p.id());
                obj.set("vector", toArray(p.vector()));

                ObjectNode payload = Json.MAPPER.createObjectNode();
                payload.put("faqId", p.faqId());
                payload.put("question", p.question());
                payload.put("answer", p.answer());
                payload.put("categoryId", p.categoryId());
                if (p.categoryName() != null) payload.put("categoryName", p.categoryName());
                if (p.docId() != null) payload.put("docId", p.docId());
                if (p.docTitle() != null) payload.put("docTitle", p.docTitle());
                if (p.uploadId() != null) payload.put("uploadId", p.uploadId());
                if (p.questionType() != null) payload.put("questionType", p.questionType());
                if (p.approvedBy() != null) payload.put("approvedBy", p.approvedBy());
                if (p.createdAt() != null) payload.put("createdAt", p.createdAt().toString());

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
                throw new RuntimeException("Qdrant FAQ upsert HTTP " + resp.statusCode() + ": " + resp.body());
            }

            log.debug("Upserted {} FAQ points to collection '{}'", points.size(), collection);
        } catch (Exception e) {
            throw new RuntimeException("Qdrant FAQ upsert failed", e);
        }
    }

    /**
     * Search FAQs by question embedding
     */
    public List<FaqSearchResult> searchFaqs(List<Double> queryVector, int topK, String categoryFilter, double minScore) {
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
                throw new RuntimeException("Qdrant FAQ search HTTP " + resp.statusCode() + ": " + resp.body());
            }

            return parseFaqResults(resp.body());
        } catch (Exception e) {
            throw new RuntimeException("Qdrant FAQ search failed", e);
        }
    }

    /**
     * Delete a FAQ point by ID
     */
    public void deleteFaq(String pointId) {
        deleteFaqs(List.of(pointId));
    }

    /**
     * Delete multiple FAQ points by IDs
     */
    public void deleteFaqs(List<String> pointIds) {
        ensureCollectionExists();

        try {
            ObjectNode body = Json.MAPPER.createObjectNode();
            ArrayNode ids = Json.MAPPER.createArrayNode();
            pointIds.forEach(ids::add);
            body.set("points", ids);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collection + "/points/delete?wait=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Qdrant FAQ delete HTTP " + resp.statusCode() + ": " + resp.body());
            }

            log.debug("Deleted {} FAQ points from collection '{}'", pointIds.size(), collection);
        } catch (Exception e) {
            throw new RuntimeException("Qdrant FAQ delete failed", e);
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
            long vectorsCount = result.path("vectors_count").asLong(0);
            String status = result.path("status").asText("unknown");

            return Map.of(
                    "collection", collection,
                    "pointsCount", pointsCount,
                    "vectorsCount", vectorsCount,
                    "status", status
            );
        } catch (Exception e) {
            log.error("Failed to get FAQ collection stats", e);
            return Map.of("error", e.getMessage());
        }
    }

    private static ArrayNode toArray(List<Double> v) {
        ArrayNode a = Json.MAPPER.createArrayNode();
        for (Double d : v) a.add(d);
        return a;
    }

    private static List<FaqSearchResult> parseFaqResults(String json) {
        try {
            JsonNode root = Json.MAPPER.readTree(json);
            JsonNode result = root.get("result");
            if (result == null || !result.isArray()) return List.of();

            List<FaqSearchResult> out = new ArrayList<>();
            for (JsonNode hit : result) {
                JsonNode payload = hit.get("payload");
                if (payload == null) continue;

                JsonNode scoreNode = hit.get("score");
                double score = scoreNode != null ? scoreNode.asDouble() : 0.0;

                out.add(new FaqSearchResult(
                        getTextOrNull(payload, "faqId"),
                        getTextOrNull(payload, "question"),
                        getTextOrNull(payload, "answer"),
                        getTextOrNull(payload, "categoryId"),
                        getTextOrNull(payload, "categoryName"),
                        getTextOrNull(payload, "docId"),
                        getTextOrNull(payload, "docTitle"),
                        score
                ));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Bad Qdrant FAQ search JSON", e);
        }
    }

    private static String getTextOrNull(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return f != null && f.isTextual() ? f.asText() : null;
    }
}
