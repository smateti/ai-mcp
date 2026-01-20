package com.example.chat.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * llama.cpp chat client using OpenAI-compatible format.
 * Includes system prompt to ensure normal text responses (not JSON tool calls).
 * Endpoint: POST /v1/chat/completions
 */
public final class LlamaCppOpenAIChatClient implements ChatClient {
  private static final Logger logger = LoggerFactory.getLogger(LlamaCppOpenAIChatClient.class);
  private final String baseUrl;
  private final String model;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public LlamaCppOpenAIChatClient(String baseUrl, String model) {
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
    logger.debug("LlamaCppOpenAIChatClient.chatOnce called - baseUrl={}, model={}", baseUrl, model);
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", model);
      body.put("temperature", temperature);
      body.put("max_tokens", maxTokens);
      body.put("stream", false);

      // Build messages array with system prompt to avoid JSON tool-call format
      ArrayNode messages = body.putArray("messages");

      // System prompt to prevent automatic function-calling format
      // but allow JSON when the user prompt explicitly requests it
      messages.addObject()
          .put("role", "system")
          .put("content", "You are a helpful assistant. Follow the user's instructions exactly. If they ask for JSON output, provide valid JSON. Do not automatically convert responses into function call format unless explicitly requested.");

      // User message
      messages.addObject()
          .put("role", "user")
          .put("content", userPrompt);

      String requestBody = objectMapper.writeValueAsString(body);
      logger.debug("Request to llama.cpp: {}", requestBody.substring(0, Math.min(200, requestBody.length())));

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/v1/chat/completions"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build();

      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      logger.debug("Response status: {}", resp.statusCode());

      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("llama.cpp OpenAI chat HTTP " + resp.statusCode() + ": " + resp.body());
      }

      JsonNode root = objectMapper.readTree(resp.body());
      JsonNode content = root.at("/choices/0/message/content");
      String result = content.isTextual() ? content.asText() : resp.body();
      logger.debug("Extracted content (first 300 chars): {}", result.substring(0, Math.min(300, result.length())));
      return result;
    } catch (Exception e) {
      logger.error("llama.cpp OpenAI chat failed", e);
      throw new RuntimeException("llama.cpp OpenAI chat failed", e);
    }
  }

  @Override
  public void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", model);
      body.put("temperature", temperature);
      body.put("max_tokens", maxTokens);
      body.put("stream", true);

      // Build messages array with system prompt
      ArrayNode messages = body.putArray("messages");

      // System prompt to prevent automatic function-calling format
      // but allow JSON when the user prompt explicitly requests it
      messages.addObject()
          .put("role", "system")
          .put("content", "You are a helpful assistant. Follow the user's instructions exactly. If they ask for JSON output, provide valid JSON. Do not automatically convert responses into function call format unless explicitly requested.");

      // User message
      messages.addObject()
          .put("role", "user")
          .put("content", userPrompt);

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/v1/chat/completions"))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      HttpResponse<java.io.InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("llama.cpp OpenAI streaming HTTP " + resp.statusCode());
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;

          if (line.startsWith("data: ")) {
            String jsonData = line.substring(6).trim();

            if ("[DONE]".equals(jsonData)) {
              break;
            }

            JsonNode chunk = objectMapper.readTree(jsonData);
            JsonNode delta = chunk.at("/choices/0/delta/content");
            if (delta.isTextual() && !delta.asText().isEmpty()) {
              onToken.accept(delta.asText());
            }
          }
        }
      }

    } catch (Exception e) {
      throw new RuntimeException("llama.cpp OpenAI streaming chat failed", e);
    }
  }
}
