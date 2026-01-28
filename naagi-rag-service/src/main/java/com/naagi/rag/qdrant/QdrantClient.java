package com.naagi.rag.qdrant;

import com.naagi.rag.http.Http;
import com.naagi.rag.json.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QdrantClient {
    private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);

    private final String baseUrl;
    private final String collection;
    private final int vectorSize;
    private final String distance;

    private volatile boolean ensured = false;

    public QdrantClient(String baseUrl, String collection, int vectorSize, String distance) {
        this.baseUrl = baseUrl;
        this.collection = collection;
        this.vectorSize = vectorSize;
        this.distance = (distance == null || distance.isBlank()) ? "Cosine" : distance;
    }

    public record Point(String id, List<Double> vector, Map<String, Object> payload) {}

    public record SearchResultWithScore(
            String docId,
            int chunkIndex,
            String text,
            String title,
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
                    return;
                }
                if (getResp.statusCode() != 404) {
                    throw new RuntimeException("Qdrant GET collection HTTP " + getResp.statusCode() + ": " + getResp.body());
                }

                ObjectNode vectors = Json.MAPPER.createObjectNode()
                        .put("size", vectorSize)
                        .put("distance", distance);

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

                ensured = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to ensure Qdrant collection exists", e);
            }
        }
    }

    public void upsertBatch(List<Point> points) {
        ensureCollectionExists();

        try {
            ArrayNode arr = Json.MAPPER.createArrayNode();
            for (Point p : points) {
                if (p.vector() == null || p.vector().size() != vectorSize) {
                    throw new IllegalArgumentException("Vector dimension mismatch for id=" + p.id()
                            + " expected=" + vectorSize
                            + " got=" + (p.vector() == null ? "null" : p.vector().size()));
                }

                ObjectNode obj = Json.MAPPER.createObjectNode();
                obj.put("id", p.id());
                obj.set("vector", toArray(p.vector()));
                obj.set("payload", Json.MAPPER.valueToTree(p.payload()));
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
                throw new RuntimeException("Qdrant upsert HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Qdrant upsert failed", e);
        }
    }

    public List<String> searchPayloadTexts(List<Double> queryVector, int topK) {
        return searchPayloadTexts(queryVector, topK, null);
    }

    public List<String> searchPayloadTexts(List<Double> queryVector, int topK, String categoryFilter) {
        ensureCollectionExists();

        try {
            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("vector", toArray(queryVector));
            body.put("limit", topK);
            body.put("with_payload", true);

            if (categoryFilter != null && !categoryFilter.isBlank()) {
                ObjectNode matchAny = Json.MAPPER.createObjectNode();
                matchAny.set("any", Json.MAPPER.createArrayNode().add(categoryFilter));

                ObjectNode keyFilter = Json.MAPPER.createObjectNode();
                keyFilter.put("key", "categories");
                keyFilter.set("match", matchAny);

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
                throw new RuntimeException("Qdrant search HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return parseTexts(resp.body());
        } catch (Exception e) {
            throw new RuntimeException("Qdrant search failed", e);
        }
    }

    public List<SearchResultWithScore> searchWithScores(List<Double> queryVector, int topK) {
        return searchWithScores(queryVector, topK, null);
    }

    public List<SearchResultWithScore> searchWithScores(List<Double> queryVector, int topK, String categoryFilter) {
        long startTime = System.currentTimeMillis();
        ensureCollectionExists();

        try {
            long buildStart = System.currentTimeMillis();
            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("vector", toArray(queryVector));
            body.put("limit", topK);
            body.put("with_payload", true);

            if (categoryFilter != null && !categoryFilter.isBlank()) {
                ObjectNode matchAny = Json.MAPPER.createObjectNode();
                matchAny.set("any", Json.MAPPER.createArrayNode().add(categoryFilter));

                ObjectNode keyFilter = Json.MAPPER.createObjectNode();
                keyFilter.put("key", "categories");
                keyFilter.set("match", matchAny);

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
            long buildTime = System.currentTimeMillis() - buildStart;

            long httpStart = System.currentTimeMillis();
            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            long httpTime = System.currentTimeMillis() - httpStart;

            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Qdrant search HTTP " + resp.statusCode() + ": " + resp.body());
            }

            long parseStart = System.currentTimeMillis();
            List<SearchResultWithScore> results = parseResultsWithScores(resp.body());
            long parseTime = System.currentTimeMillis() - parseStart;

            long totalTime = System.currentTimeMillis() - startTime;
            log.debug("[QDRANT TIMING] total={}ms (build={}ms, http={}ms, parse={}ms) topK={} filter={}",
                    totalTime, buildTime, httpTime, parseTime, topK, categoryFilter);

            return results;
        } catch (Exception e) {
            throw new RuntimeException("Qdrant search with scores failed", e);
        }
    }

    private static ArrayNode toArray(List<Double> v) {
        ArrayNode a = Json.MAPPER.createArrayNode();
        for (Double d : v) a.add(d);
        return a;
    }

    private static List<String> parseTexts(String json) {
        try {
            JsonNode root = Json.MAPPER.readTree(json);
            JsonNode result = root.get("result");
            if (result == null || !result.isArray()) return List.of();

            List<String> out = new ArrayList<>();
            for (JsonNode hit : result) {
                JsonNode payload = hit.get("payload");
                if (payload == null) continue;
                JsonNode text = payload.get("text");
                if (text != null && text.isTextual()) out.add(text.asText());
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Bad Qdrant search JSON", e);
        }
    }

    private static List<SearchResultWithScore> parseResultsWithScores(String json) {
        try {
            JsonNode root = Json.MAPPER.readTree(json);
            JsonNode result = root.get("result");
            if (result == null || !result.isArray()) return List.of();

            List<SearchResultWithScore> out = new ArrayList<>();
            for (JsonNode hit : result) {
                JsonNode payload = hit.get("payload");
                if (payload == null) continue;

                JsonNode docIdNode = payload.get("docId");
                JsonNode chunkIndexNode = payload.get("chunkIndex");
                JsonNode textNode = payload.get("text");
                JsonNode titleNode = payload.get("title");
                JsonNode scoreNode = hit.get("score");

                if (docIdNode != null && chunkIndexNode != null && textNode != null && scoreNode != null) {
                    out.add(new SearchResultWithScore(
                            docIdNode.asText(),
                            chunkIndexNode.asInt(),
                            textNode.asText(),
                            titleNode != null ? titleNode.asText() : null,
                            scoreNode.asDouble()
                    ));
                }
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Bad Qdrant search JSON (with scores)", e);
        }
    }
}
