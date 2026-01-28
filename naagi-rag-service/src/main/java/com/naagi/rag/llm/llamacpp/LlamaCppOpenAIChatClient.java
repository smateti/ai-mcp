package com.naagi.rag.llm.llamacpp;

import com.naagi.rag.http.Http;
import com.naagi.rag.json.Json;
import com.naagi.rag.llm.ChatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

public final class LlamaCppOpenAIChatClient implements ChatClient {
    private static final Logger log = LoggerFactory.getLogger(LlamaCppOpenAIChatClient.class);

    private final String baseUrl;
    private final String model;

    public LlamaCppOpenAIChatClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String chatOnce(String userPrompt, double temperature, int maxTokens) {
        long startTime = System.currentTimeMillis();
        try {
            long buildStart = System.currentTimeMillis();
            ObjectNode body = Json.MAPPER.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);
            // Explicitly disable function/tool calling to prevent the model from
            // returning function call format instead of actual content
            body.put("tool_choice", "none");

            ArrayNode messages = body.putArray("messages");

            messages.addObject()
                    .put("role", "system")
                    .put("content", "You are a helpful assistant. Always respond in clear, natural language. Do not use function calls, tool calls, or JSON format in your responses. Write answers as readable text with proper sentences.");

            messages.addObject()
                    .put("role", "user")
                    .put("content", userPrompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(body)))
                    .build();
            long buildTime = System.currentTimeMillis() - buildStart;

            long httpStart = System.currentTimeMillis();
            HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            long httpTime = System.currentTimeMillis() - httpStart;

            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("llama.cpp OpenAI chat HTTP " + resp.statusCode() + ": " + resp.body());
            }

            long parseStart = System.currentTimeMillis();
            JsonNode root = Json.MAPPER.readTree(resp.body());
            JsonNode message = root.at("/choices/0/message");

            // Check for function call response first
            JsonNode toolCalls = message.at("/tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                // If it's a function call, try to extract the arguments which might contain JSON
                JsonNode args = toolCalls.get(0).at("/function/arguments");
                if (args.isTextual()) {
                    long parseTime = System.currentTimeMillis() - parseStart;
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.debug("[CHAT TIMING] total={}ms (build={}ms, http={}ms, parse={}ms) promptLen={} maxTokens={}",
                            totalTime, buildTime, httpTime, parseTime, userPrompt.length(), maxTokens);
                    return args.asText();
                }
            }

            // Check for regular content
            JsonNode content = message.at("/content");
            long parseTime = System.currentTimeMillis() - parseStart;
            long totalTime = System.currentTimeMillis() - startTime;
            log.debug("[CHAT TIMING] total={}ms (build={}ms, http={}ms, parse={}ms) promptLen={} maxTokens={}",
                    totalTime, buildTime, httpTime, parseTime, userPrompt.length(), maxTokens);

            if (content.isTextual() && !content.asText().isBlank()) {
                return content.asText();
            }

            // Fallback: return raw body for debugging
            return resp.body();
        } catch (Exception e) {
            throw new RuntimeException("llama.cpp OpenAI chat failed", e);
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
            // Explicitly disable function/tool calling
            body.put("tool_choice", "none");

            ArrayNode messages = body.putArray("messages");

            messages.addObject()
                    .put("role", "system")
                    .put("content", "You are a helpful assistant. Always respond in clear, natural language. Do not use function calls, tool calls, or JSON format in your responses. Write answers as readable text with proper sentences.");

            messages.addObject()
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

                        JsonNode chunk = Json.MAPPER.readTree(jsonData);
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
