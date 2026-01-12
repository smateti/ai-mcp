package com.example.rag.llama;

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
 * llama.cpp OpenAI-compatible chat client (non-stream).
 * Endpoint: POST /v1/chat/completions
 */
public final class LlamaChatClient implements ChatClient {
  private final String baseUrl;
  private final String model;

  public LlamaChatClient(String baseUrl, String model) {
    this.baseUrl = baseUrl;
    this.model = model;
  }

  @Override
  public String chatOnce(String userPrompt, double temperature, int maxTokens) {
    try {
      ObjectNode body = Json.MAPPER.createObjectNode();
      body.put("model", model);
      body.put("stream", false);
      body.put("temperature", temperature);
      body.put("max_tokens", maxTokens);

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
        throw new RuntimeException("Chat HTTP " + resp.statusCode() + ": " + resp.body());
      }
      return parseChat(resp.body());
    } catch (Exception e) {
      throw new RuntimeException("Chat failed", e);
    }
  }

  @Override
  public void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken) {
    try {
      ObjectNode body = Json.MAPPER.createObjectNode();
      body.put("model", model);
      body.put("stream", true); // Enable streaming
      body.put("temperature", temperature);
      body.put("max_tokens", maxTokens);

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

      // Use streaming response handler
      HttpResponse<java.io.InputStream> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());

      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("Chat streaming HTTP " + resp.statusCode());
      }

      // Read stream line by line (SSE format: "data: {...}\n\n")
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;

          // llama.cpp uses SSE format: "data: {...}"
          if (line.startsWith("data: ")) {
            String jsonData = line.substring(6).trim();

            // Check for completion marker
            if (jsonData.equals("[DONE]")) {
              break;
            }

            // Parse JSON chunk
            try {
              JsonNode chunk = Json.MAPPER.readTree(jsonData);

              // Extract token from choices[0].delta.content
              JsonNode deltaContent = chunk.at("/choices/0/delta/content");
              if (deltaContent.isTextual() && !deltaContent.asText().isEmpty()) {
                onToken.accept(deltaContent.asText());
              }

              // Check if done
              JsonNode finishReason = chunk.at("/choices/0/finish_reason");
              if (!finishReason.isMissingNode() && !finishReason.isNull()) {
                break;
              }
            } catch (Exception e) {
              // Skip malformed JSON lines
            }
          }
        }
      }

    } catch (Exception e) {
      throw new RuntimeException("Chat streaming failed", e);
    }
  }

  static String parseChat(String json) {
    try {
      JsonNode root = Json.MAPPER.readTree(json);
      JsonNode content = root.at("/choices/0/message/content");
      if (!content.isMissingNode() && content.isTextual()) return content.asText();
      JsonNode text = root.at("/choices/0/text");
      if (!text.isMissingNode() && text.isTextual()) return text.asText();
      JsonNode response = root.get("response");
      if (response != null && response.isTextual()) return response.asText();
      return json;
    } catch (Exception e) {
      throw new RuntimeException("Bad chat JSON", e);
    }
  }
}
