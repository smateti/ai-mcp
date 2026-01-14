package com.example.rag.openai;

import com.example.rag.http.Http;
import com.example.rag.json.Json;
import com.example.rag.llm.ChatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * OpenAI-compatible chat client (works with Ollama's /v1/chat/completions endpoint).
 * Endpoint: POST /v1/chat/completions
 * OpenAI format: https://platform.openai.com/docs/api-reference/chat/create
 */
public final class OpenAIChatClient implements ChatClient {
  private final String baseUrl;
  private final String model;

  public OpenAIChatClient(String baseUrl, String model) {
    this.baseUrl = baseUrl;
    this.model = model;
  }

  @Override
  public String chatOnce(String userPrompt, double temperature, int maxTokens) {
    try {
      ObjectNode body = Json.MAPPER.createObjectNode();
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
          .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
          .build();

      HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("OpenAI-compatible chat HTTP " + resp.statusCode() + ": " + resp.body());
      }

      JsonNode root = Json.MAPPER.readTree(resp.body());
      JsonNode content = root.at("/choices/0/message/content");
      return content.isTextual() ? content.asText() : resp.body();
    } catch (Exception e) {
      throw new RuntimeException("OpenAI-compatible chat failed", e);
    }
  }

  @Override
  public void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken) {
    try {
      ObjectNode body = Json.MAPPER.createObjectNode();
      body.put("model", model);
      body.put("temperature", temperature);
      body.put("max_tokens", maxTokens);
      body.put("stream", true);

      body.putArray("messages")
          .addObject()
          .put("role", "user")
          .put("content", userPrompt);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/v1/chat/completions"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
          .build();

      HttpResponse<java.io.InputStream> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());

      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("OpenAI-compatible streaming HTTP " + resp.statusCode());
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;

          if (line.startsWith("data: ")) {
            String jsonData = line.substring(6).trim();
            if ("[DONE]".equals(jsonData)) break;

            JsonNode chunk = Json.MAPPER.readTree(jsonData);
            JsonNode delta = chunk.at("/choices/0/delta/content");
            if (delta.isTextual() && !delta.asText().isEmpty()) {
              onToken.accept(delta.asText());
            }
          }
        }
      }

    } catch (Exception e) {
      throw new RuntimeException("OpenAI-compatible streaming failed", e);
    }
  }
}
