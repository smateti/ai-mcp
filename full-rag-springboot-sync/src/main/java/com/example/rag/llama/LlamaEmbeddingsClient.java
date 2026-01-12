package com.example.rag.llama;

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

public final class LlamaEmbeddingsClient implements EmbeddingsClient {
  private final String baseUrl;
  private final String model;

  public LlamaEmbeddingsClient(String baseUrl, String model) {
    this.baseUrl = baseUrl;
    this.model = model;
  }

  public List<Double> embed(String text) {
    try {
      // Split text into chunks if too large (batch size is 512 tokens, use 100 words max to stay well under limit)
      List<String> chunks = chunkText(text, 100);

      if (chunks.size() == 1) {
        // Single chunk - embed directly
        return embedSingleChunk(chunks.get(0));
      } else {
        // Multiple chunks - embed each and average
        List<List<Double>> embeddings = new ArrayList<>();
        for (String chunk : chunks) {
          embeddings.add(embedSingleChunk(chunk));
        }
        return averageEmbeddings(embeddings);
      }
    } catch (Exception e) {
      throw new RuntimeException("Embedding failed", e);
    }
  }

  private List<String> chunkText(String text, int maxWords) {
    String[] words = text.split("\\s+");
    List<String> chunks = new ArrayList<>();

    if (words.length <= maxWords) {
      chunks.add(text);
      return chunks;
    }

    // Split into chunks with some overlap
    int overlap = 25; // 25 words overlap between chunks
    for (int i = 0; i < words.length; i += maxWords - overlap) {
      int end = Math.min(i + maxWords, words.length);
      StringBuilder chunk = new StringBuilder();
      for (int j = i; j < end; j++) {
        if (j > i) chunk.append(" ");
        chunk.append(words[j]);
      }
      chunks.add(chunk.toString());

      if (end >= words.length) break;
    }

    return chunks;
  }

  private List<Double> embedSingleChunk(String text) throws Exception {
    ObjectNode body = Json.MAPPER.createObjectNode()
        .put("content", text)
        .put("model", model);

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/embedding"))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
        .build();

    HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() / 100 != 2) {
      throw new RuntimeException("Embed HTTP " + resp.statusCode() + ": " + resp.body());
    }
    return parse(resp.body());
  }

  private List<Double> averageEmbeddings(List<List<Double>> embeddings) {
    if (embeddings.isEmpty()) {
      throw new IllegalArgumentException("No embeddings to average");
    }

    int dimension = embeddings.get(0).size();
    List<Double> averaged = new ArrayList<>(dimension);

    for (int i = 0; i < dimension; i++) {
      double sum = 0.0;
      for (List<Double> emb : embeddings) {
        sum += emb.get(i);
      }
      averaged.add(sum / embeddings.size());
    }

    return averaged;
  }

  static List<Double> parse(String json) {
    try {
      JsonNode root = Json.MAPPER.readTree(json);
      if (!root.isArray() || root.size() == 0) throw new IllegalStateException("Expected root array");
      JsonNode emb2d = root.get(0).get("embedding");
      if (emb2d == null || !emb2d.isArray() || emb2d.size() == 0 || !emb2d.get(0).isArray()) {
        throw new IllegalStateException("Expected embedding 2D array");
      }
      JsonNode vec = emb2d.get(0);
      List<Double> out = new ArrayList<>(vec.size());
      for (JsonNode n : vec) out.add(n.asDouble());
      return out;
    } catch (Exception e) {
      throw new RuntimeException("Bad embedding JSON", e);
    }
  }
}
