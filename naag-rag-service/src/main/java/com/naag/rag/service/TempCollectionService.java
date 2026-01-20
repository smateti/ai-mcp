package com.naag.rag.service;

import com.naag.rag.chunk.HybridChunker;
import com.naag.rag.entity.DocumentUpload;
import com.naag.rag.http.Http;
import com.naag.rag.json.Json;
import com.naag.rag.llm.ChatClient;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.qdrant.QdrantClient;
import com.naag.rag.qdrant.QdrantClient.Point;
import com.naag.rag.qdrant.QdrantClient.SearchResultWithScore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TempCollectionService {

    private final String qdrantBaseUrl;
    private final String mainCollection;
    private final int vectorSize;
    private final String distance;
    private final EmbeddingsClient embeddingsClient;
    private final ChatClient chatClient;
    private final int maxChars;
    private final int overlapChars;
    private final int minChars;
    private final int batchSize;

    public TempCollectionService(
            @Value("${naag.rag.qdrant.baseUrl}") String qdrantBaseUrl,
            @Value("${naag.rag.qdrant.collection}") String mainCollection,
            @Value("${naag.rag.qdrant.vectorSize}") int vectorSize,
            @Value("${naag.rag.qdrant.distance:Cosine}") String distance,
            @Value("${naag.rag.chunking.maxChars:1200}") int maxChars,
            @Value("${naag.rag.chunking.overlapChars:200}") int overlapChars,
            @Value("${naag.rag.chunking.minChars:100}") int minChars,
            @Value("${naag.rag.performance.qdrantBatchSize:64}") int batchSize,
            EmbeddingsClient embeddingsClient,
            ChatClient chatClient) {
        this.qdrantBaseUrl = qdrantBaseUrl;
        this.mainCollection = mainCollection;
        this.vectorSize = vectorSize;
        this.distance = distance;
        this.embeddingsClient = embeddingsClient;
        this.chatClient = chatClient;
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
        this.minChars = minChars;
        this.batchSize = batchSize;
    }

    public String getTempCollectionName(String uploadId) {
        return "temp_" + uploadId.replace("-", "_");
    }

    public int chunkAndStoreTemp(DocumentUpload upload) {
        String tempCollection = getTempCollectionName(upload.getId());

        // Create temp collection
        createCollection(tempCollection);

        // Chunk the document
        HybridChunker chunker = new HybridChunker(maxChars, overlapChars, minChars);
        List<String> chunks = chunker.chunk(upload.getOriginalContent());

        if (chunks.isEmpty()) {
            log.warn("No chunks generated for upload {}", upload.getId());
            return 0;
        }

        // Store chunks in temp collection
        List<Point> batch = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<Double> vector = embeddingsClient.embed(chunk);

            Map<String, Object> payload = new HashMap<>();
            payload.put("docId", upload.getDocId());
            payload.put("uploadId", upload.getId());
            payload.put("chunkIndex", i);
            payload.put("text", chunk);
            if (upload.getCategoryId() != null) {
                payload.put("categories", List.of(upload.getCategoryId()));
            }

            Point point = new Point(
                    stableId(upload.getId() + ":" + i + ":" + chunk),
                    vector,
                    payload
            );

            batch.add(point);

            if (batch.size() >= batchSize) {
                upsertBatch(tempCollection, batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            upsertBatch(tempCollection, batch);
        }

        log.info("Stored {} chunks in temp collection {} for upload {}",
                chunks.size(), tempCollection, upload.getId());

        return chunks.size();
    }

    public String queryTemp(String uploadId, String question, int topK) {
        String tempCollection = getTempCollectionName(uploadId);

        List<Double> queryVector = embeddingsClient.embed(question);
        List<SearchResultWithScore> results = searchWithScores(tempCollection, queryVector, topK);

        if (results.isEmpty()) {
            return "No relevant information found.";
        }

        String contextBlock = results.stream()
                .map(SearchResultWithScore::text)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
                Answer using ONLY the context below. Be concise and accurate.

                Context:
                %s

                Question: %s
                Answer:""".formatted(contextBlock, question);

        return chatClient.chatOnce(prompt, 0.2, 256);
    }

    public void moveToMainCollection(DocumentUpload upload) {
        String tempCollection = getTempCollectionName(upload.getId());

        // Get all points from temp collection
        List<Map<String, Object>> points = getAllPoints(tempCollection);

        if (points.isEmpty()) {
            log.warn("No points found in temp collection for upload {}", upload.getId());
            return;
        }

        // Re-chunk and store in main collection (for clean slate)
        HybridChunker chunker = new HybridChunker(maxChars, overlapChars, minChars);
        List<String> chunks = chunker.chunk(upload.getOriginalContent());

        List<Point> batch = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<Double> vector = embeddingsClient.embed(chunk);

            Map<String, Object> payload = new HashMap<>();
            payload.put("docId", upload.getDocId());
            payload.put("chunkIndex", i);
            payload.put("text", chunk);
            if (upload.getCategoryId() != null) {
                payload.put("categories", List.of(upload.getCategoryId()));
            }
            if (upload.getTitle() != null) {
                payload.put("title", upload.getTitle());
            }

            Point point = new Point(
                    stableId(upload.getDocId() + ":" + i + ":" + chunk),
                    vector,
                    payload
            );

            batch.add(point);

            if (batch.size() >= batchSize) {
                upsertBatchToMain(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            upsertBatchToMain(batch);
        }

        log.info("Moved {} chunks from temp to main collection for upload {}",
                chunks.size(), upload.getId());
    }

    public void deleteTempCollection(String uploadId) {
        String tempCollection = getTempCollectionName(uploadId);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + tempCollection))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();

            HttpResponse<String> response = Http.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 == 2 || response.statusCode() == 404) {
                log.info("Deleted temp collection: {}", tempCollection);
            } else {
                log.warn("Failed to delete temp collection {}: HTTP {}",
                        tempCollection, response.statusCode());
            }
        } catch (Exception e) {
            log.error("Error deleting temp collection {}", tempCollection, e);
        }
    }

    private void createCollection(String collection) {
        try {
            // Check if exists
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + collection))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> getResp = Http.CLIENT.send(getReq, HttpResponse.BodyHandlers.ofString());

            if (getResp.statusCode() == 200) {
                // Already exists, delete and recreate for fresh start
                deleteTempCollection(collection.replace("temp_", ""));
            }

            // Create collection
            ObjectNode vectors = Json.MAPPER.createObjectNode()
                    .put("size", vectorSize)
                    .put("distance", distance);

            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("vectors", vectors);

            HttpRequest putReq = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + collection))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> putResp = Http.CLIENT.send(putReq, HttpResponse.BodyHandlers.ofString());

            if (putResp.statusCode() / 100 != 2) {
                throw new RuntimeException("Failed to create collection " + collection +
                        ": HTTP " + putResp.statusCode());
            }

            log.info("Created temp collection: {}", collection);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create collection " + collection, e);
        }
    }

    private void upsertBatch(String collection, List<Point> points) {
        try {
            ArrayNode arr = Json.MAPPER.createArrayNode();
            for (Point p : points) {
                ObjectNode obj = Json.MAPPER.createObjectNode();
                obj.put("id", p.id());
                obj.set("vector", toArray(p.vector()));
                obj.set("payload", Json.MAPPER.valueToTree(p.payload()));
                arr.add(obj);
            }

            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("points", arr);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + collection + "/points?wait=true"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Qdrant upsert to " + collection +
                        " HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert to " + collection, e);
        }
    }

    private void upsertBatchToMain(List<Point> points) {
        upsertBatch(mainCollection, points);
    }

    private List<SearchResultWithScore> searchWithScores(String collection, List<Double> queryVector, int topK) {
        try {
            ObjectNode body = Json.MAPPER.createObjectNode();
            body.set("vector", toArray(queryVector));
            body.put("limit", topK);
            body.put("with_payload", true);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + collection + "/points/search"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Qdrant search HTTP " + resp.statusCode() + ": " + resp.body());
            }

            return parseResultsWithScores(resp.body());
        } catch (Exception e) {
            throw new RuntimeException("Qdrant search failed for " + collection, e);
        }
    }

    private List<Map<String, Object>> getAllPoints(String collection) {
        try {
            ObjectNode body = Json.MAPPER.createObjectNode();
            body.put("limit", 10000);
            body.put("with_payload", true);
            body.put("with_vector", true);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(qdrantBaseUrl + "/collections/" + collection + "/points/scroll"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Failed to scroll points from {}: HTTP {}", collection, resp.statusCode());
                return List.of();
            }

            JsonNode root = Json.MAPPER.readTree(resp.body());
            JsonNode result = root.get("result");
            if (result == null) return List.of();

            JsonNode points = result.get("points");
            if (points == null || !points.isArray()) return List.of();

            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode point : points) {
                Map<String, Object> p = new HashMap<>();
                p.put("id", point.get("id").asText());
                p.put("payload", point.get("payload"));
                p.put("vector", point.get("vector"));
                out.add(p);
            }
            return out;
        } catch (Exception e) {
            log.error("Failed to get all points from {}", collection, e);
            return List.of();
        }
    }

    private static ArrayNode toArray(List<Double> v) {
        ArrayNode a = Json.MAPPER.createArrayNode();
        for (Double d : v) a.add(d);
        return a;
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
                JsonNode scoreNode = hit.get("score");

                if (docIdNode != null && chunkIndexNode != null && textNode != null && scoreNode != null) {
                    out.add(new SearchResultWithScore(
                            docIdNode.asText(),
                            chunkIndexNode.asInt(),
                            textNode.asText(),
                            scoreNode.asDouble()
                    ));
                }
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Bad Qdrant search JSON", e);
        }
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
