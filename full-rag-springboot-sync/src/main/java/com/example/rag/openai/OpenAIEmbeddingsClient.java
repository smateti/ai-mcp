package com.example.rag.openai;

import com.example.rag.http.Http;
import com.example.rag.json.Json;
import com.example.rag.llm.EmbeddingsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible embeddings client (works with Ollama's /v1/embeddings endpoint).
 * Endpoint: POST /v1/embeddings
 * OpenAI format: https://platform.openai.com/docs/api-reference/embeddings/create
 *
 * Request: { "model": "...", "input": "text" }
 * Response: { "data": [ { "embedding": [...] } ] }
 */
public final class OpenAIEmbeddingsClient implements EmbeddingsClient {
  private final String baseUrl;
  private final String model;

  public OpenAIEmbeddingsClient(String baseUrl, String model) {
    this.baseUrl = baseUrl;
    this.model = model;
  }

  @Override
  public List<Double> embed(String text) {
    try {
      ObjectNode body = Json.MAPPER.createObjectNode()
          .put("model", model)
          .put("input", text);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/v1/embeddings"))
          .timeout(Duration.ofSeconds(30))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
          .build();

      HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("OpenAI-compatible embed HTTP " + resp.statusCode() + ": " + resp.body());
      }

      JsonNode root = Json.MAPPER.readTree(resp.body());

      // OpenAI format: { "data": [ { "embedding": [...] } ] }
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
      return out;
    } catch (Exception e) {
      throw new RuntimeException("OpenAI-compatible embedding failed", e);
    }
  }
}
