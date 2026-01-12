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
 * Ollama chat client via REST (non-stream).
 * Endpoint: POST /api/chat
 */
public final class OllamaChatClient implements ChatClient {
  private final String baseUrl;
  private final String model;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OllamaChatClient(String baseUrl, String model) {
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
      body.put("stream", false);

      body.putArray("messages")
          .addObject()
          .put("role", "user")
          .put("content", userPrompt);

      ObjectNode opts = body.putObject("options");
      opts.put("temperature", temperature);
      opts.put("num_predict", maxTokens);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/chat"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("Ollama chat HTTP " + resp.statusCode() + ": " + resp.body());
      }

      JsonNode root = objectMapper.readTree(resp.body());
      JsonNode content = root.at("/message/content");
      return content.isTextual() ? content.asText() : resp.body();
    } catch (Exception e) {
      throw new RuntimeException("Ollama chat failed", e);
    }
  }

  @Override
  public void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", model);
      body.put("stream", true); // Enable streaming

      body.putArray("messages")
          .addObject()
          .put("role", "user")
          .put("content", userPrompt);

      ObjectNode opts = body.putObject("options");
      opts.put("temperature", temperature);
      opts.put("num_predict", maxTokens);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/chat"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      // Use streaming response handler
      HttpResponse<java.io.InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("Ollama chat HTTP " + resp.statusCode());
      }

      // Read stream line by line
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;

          // Parse each JSON line
          JsonNode chunk = objectMapper.readTree(line);

          // Extract token from message.content
          JsonNode content = chunk.at("/message/content");
          if (content.isTextual() && !content.asText().isEmpty()) {
            onToken.accept(content.asText());
          }

          // Check if done
          if (chunk.has("done") && chunk.get("done").asBoolean()) {
            break;
          }
        }
      }

    } catch (Exception e) {
      throw new RuntimeException("Ollama streaming chat failed", e);
    }
  }
}
