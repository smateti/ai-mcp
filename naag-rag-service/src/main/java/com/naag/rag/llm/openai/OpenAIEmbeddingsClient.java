package com.naag.rag.llm.openai;

import com.naag.rag.http.Http;
import com.naag.rag.json.Json;
import com.naag.rag.llm.EmbeddingsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class OpenAIEmbeddingsClient implements EmbeddingsClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIEmbeddingsClient.class);

    private final String baseUrl;
    private final String model;

    public OpenAIEmbeddingsClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public List<Double> embed(String text) {
        long startTime = System.currentTimeMillis();
        try {
            long buildStart = System.currentTimeMillis();
            ObjectNode body = Json.MAPPER.createObjectNode()
                    .put("model", model)
                    .put("input", text);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/embeddings"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();
            long buildTime = System.currentTimeMillis() - buildStart;

            long httpStart = System.currentTimeMillis();
            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            long httpTime = System.currentTimeMillis() - httpStart;

            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("OpenAI-compatible embed HTTP " + resp.statusCode() + ": " + resp.body());
            }

            long parseStart = System.currentTimeMillis();
            JsonNode root = Json.MAPPER.readTree(resp.body());

            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.size() == 0) {
                throw new IllegalStateException("Bad OpenAI embed response: missing data array - " + resp.body());
            }

            JsonNode firstItem = data.get(0);
            JsonNode vec = firstItem.get("embedding");

            if (vec == null || !vec.isArray()) {
                throw new IllegalStateException("Bad OpenAI embed response: missing embedding array - " + resp.body());
            }

            List<Double> out = new ArrayList<>(vec.size());
            for (JsonNode n : vec) out.add(n.asDouble());
            long parseTime = System.currentTimeMillis() - parseStart;

            long totalTime = System.currentTimeMillis() - startTime;
            log.debug("[EMBED TIMING] total={}ms (build={}ms, http={}ms, parse={}ms) textLen={}",
                    totalTime, buildTime, httpTime, parseTime, text.length());

            return out;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI-compatible embedding failed", e);
        }
    }
}
