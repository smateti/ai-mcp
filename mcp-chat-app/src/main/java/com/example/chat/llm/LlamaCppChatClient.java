package com.example.chat.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * llama.cpp chat client (non-OpenAI format).
 * Endpoint: POST /completion
 */
public final class LlamaCppChatClient implements ChatClient {
  private final String baseUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public LlamaCppChatClient(String baseUrl, String model) {
    this.baseUrl = baseUrl;
    // Note: llama.cpp doesn't use model parameter in the API
    this.httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public String chatOnce(String userPrompt, double temperature, int maxTokens) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("prompt", userPrompt);
      body.put("temperature", temperature);
      body.put("n_predict", maxTokens);
      body.put("stream", false);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/completion"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("llama.cpp chat HTTP " + resp.statusCode() + ": " + resp.body());
      }

      JsonNode root = objectMapper.readTree(resp.body());
      JsonNode content = root.get("content");
      return content != null && content.isTextual() ? content.asText() : resp.body();
    } catch (Exception e) {
      throw new RuntimeException("llama.cpp chat failed", e);
    }
  }

  @Override
  public void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("prompt", userPrompt);
      body.put("temperature", temperature);
      body.put("n_predict", maxTokens);
      body.put("stream", true);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/completion"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      HttpResponse<java.io.InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("llama.cpp streaming HTTP " + resp.statusCode());
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;

          // llama.cpp streams JSON lines with "data: " prefix
          if (line.startsWith("data: ")) {
            String jsonData = line.substring(6).trim();

            JsonNode chunk = objectMapper.readTree(jsonData);

            // Extract token from "content" field
            JsonNode content = chunk.get("content");
            if (content != null && content.isTextual() && !content.asText().isEmpty()) {
              onToken.accept(content.asText());
            }

            // Check if generation is complete
            if (chunk.has("stop") && chunk.get("stop").asBoolean()) {
              break;
            }
          }
        }
      }

    } catch (Exception e) {
      throw new RuntimeException("llama.cpp streaming chat failed", e);
    }
  }
}
