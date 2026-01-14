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
 * OpenAI-compatible chat client (works with Ollama's /v1/chat/completions endpoint).
 * Endpoint: POST /v1/chat/completions
 */
public final class OpenAIChatClient implements ChatClient {
  private final String baseUrl;
  private final String model;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OpenAIChatClient(String baseUrl, String model) {
    this.baseUrl = baseUrl;
    this.model = model;
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
      body.put("model", model);
      body.put("temperature", temperature);
      body.put("max_tokens", maxTokens);
      body.put("stream", false);

      body.putArray("messages")
          .addObject()
          .put("role", "user")
          .put("content", userPrompt);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/v1/chat/completions"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("OpenAI-compatible chat HTTP " + resp.statusCode() + ": " + resp.body());
      }

      JsonNode root = objectMapper.readTree(resp.body());
      JsonNode content = root.at("/choices/0/message/content");
      return content.isTextual() ? content.asText() : resp.body();
    } catch (Exception e) {
      throw new RuntimeException("OpenAI-compatible chat failed", e);
    }
  }

  @Override
  public void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", model);
      body.put("temperature", temperature);
      body.put("max_tokens", maxTokens);
      body.put("stream", true); // Enable streaming

      body.putArray("messages")
          .addObject()
          .put("role", "user")
          .put("content", userPrompt);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/v1/chat/completions"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      // Use streaming response handler
      HttpResponse<java.io.InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("OpenAI-compatible streaming HTTP " + resp.statusCode());
      }

      // Read SSE stream line by line
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;

          // OpenAI format uses SSE with "data: " prefix
          if (line.startsWith("data: ")) {
            String jsonData = line.substring(6).trim();

            // Check for [DONE] signal
            if ("[DONE]".equals(jsonData)) {
              break;
            }

            // Parse JSON chunk
            JsonNode chunk = objectMapper.readTree(jsonData);

            // Extract token from choices[0].delta.content
            JsonNode delta = chunk.at("/choices/0/delta/content");
            if (delta.isTextual() && !delta.asText().isEmpty()) {
              onToken.accept(delta.asText());
            }
          }
        }
      }

    } catch (Exception e) {
      throw new RuntimeException("OpenAI-compatible streaming chat failed", e);
    }
  }
}
