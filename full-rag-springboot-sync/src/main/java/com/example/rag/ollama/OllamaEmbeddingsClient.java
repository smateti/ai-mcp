package com.example.rag.ollama;

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
 * Ollama embeddings via REST.
 * Endpoint: POST /api/embed
 * Body: { "model": "...", "input": "text" }
 * Response: { "embeddings": [[...]] }
 */
public final class OllamaEmbeddingsClient implements EmbeddingsClient {
  private final String baseUrl;
  private final String model;

  public OllamaEmbeddingsClient(String baseUrl, String model) {
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
          .uri(URI.create(baseUrl + "/api/embed"))
          .timeout(Duration.ofSeconds(30))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
          .build();

      HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("Ollama embed HTTP " + resp.statusCode() + ": " + resp.body());
      }

      JsonNode root = Json.MAPPER.readTree(resp.body());
      JsonNode arr = root.get("embeddings");
      JsonNode vec = (arr != null && arr.isArray() && arr.size() > 0) ? arr.get(0) : null;
      if (vec == null || !vec.isArray()) throw new IllegalStateException("Bad Ollama embed JSON: " + resp.body());

      List<Double> out = new ArrayList<>(vec.size());
      for (JsonNode n : vec) out.add(n.asDouble());
      return out;
    } catch (Exception e) {
      throw new RuntimeException("Ollama embedding failed", e);
    }
  }
}
