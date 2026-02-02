package com.naagi.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naagi.llm.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class LlamaCppProxy {

    private final String baseUrl;
    private final String model;
    private final int timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlamaCppProxy(
            @Value("${naagi.llm.baseUrl}") String baseUrl,
            @Value("${naagi.llm.model}") String model,
            @Value("${naagi.llm.timeout:120}") int timeout,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    // ==================== Non-Streaming Chat ====================

    public ChatResponse chat(ChatRequest request) {
        try {
            ObjectNode body = buildRequestBody(request, false);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("LLM HTTP " + response.statusCode() + ": " + response.body());
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            log.error("[LLM-PROXY] Chat failed", e);
            throw new RuntimeException("LLM chat failed: " + e.getMessage(), e);
        }
    }

    // ==================== Streaming Chat ====================

    public void chatStream(ChatRequest request, Consumer<String> onChunk, Runnable onDone) {
        try {
            ObjectNode body = buildRequestBody(request, true);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("LLM HTTP " + response.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            JsonNode chunk = objectMapper.readTree(data);
                            JsonNode delta = chunk.at("/choices/0/delta");
                            if (delta.has("content") && !delta.get("content").isNull()) {
                                String token = delta.get("content").asText();
                                if (!token.isEmpty()) {
                                    onChunk.accept(token);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("[LLM-PROXY] Failed to parse stream chunk: {}", data);
                        }
                    }
                }
            }

            onDone.run();
        } catch (Exception e) {
            log.error("[LLM-PROXY] Stream failed", e);
            throw new RuntimeException("LLM stream failed: " + e.getMessage(), e);
        }
    }

    // ==================== Streaming Chat with full response collection ====================

    public ChatResponse chatStreamCollect(ChatRequest request, Consumer<String> onToken) {
        StringBuilder fullContent = new StringBuilder();
        chatStream(request, token -> {
            fullContent.append(token);
            onToken.accept(token);
        }, () -> {});
        String content = fullContent.toString();
        return content.isBlank() ? ChatResponse.text("") : ChatResponse.text(content);
    }

    // ==================== Request Building ====================

    ObjectNode buildRequestBody(ChatRequest request, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        body.put("stream", stream);

        if (request.toolChoice() != null) {
            body.put("tool_choice", request.toolChoice());
        }

        // Messages array
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage msg : request.messages()) {
            ObjectNode msgNode = messages.addObject();
            msgNode.put("role", msg.role());
            if (msg.content() != null) {
                msgNode.put("content", msg.content());
            }
            if (msg.name() != null) {
                msgNode.put("name", msg.name());
            }
            if (msg.toolCallId() != null) {
                msgNode.put("tool_call_id", msg.toolCallId());
            }
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                ArrayNode toolCallsNode = msgNode.putArray("tool_calls");
                for (ToolCall tc : msg.toolCalls()) {
                    ObjectNode tcNode = toolCallsNode.addObject();
                    tcNode.put("id", tc.id());
                    tcNode.put("type", tc.type());
                    ObjectNode fnNode = tcNode.putObject("function");
                    fnNode.put("name", tc.function().name());
                    fnNode.put("arguments", tc.function().arguments());
                }
            }
        }

        // Tools array
        if (request.hasTools()) {
            ArrayNode toolsNode = body.putArray("tools");
            for (ToolDefinition tool : request.tools()) {
                ObjectNode toolNode = toolsNode.addObject();
                toolNode.put("type", tool.type());
                ObjectNode fnNode = toolNode.putObject("function");
                fnNode.put("name", tool.function().name());
                fnNode.put("description", tool.function().description());
                if (tool.function().parameters() != null) {
                    fnNode.set("parameters", tool.function().parameters());
                }
            }
        }

        return body;
    }

    // ==================== Response Parsing ====================

    ChatResponse parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.at("/choices/0/message");
        String finishReason = root.at("/choices/0/finish_reason").asText("stop");

        // Parse tool calls if present
        JsonNode toolCallsNode = message.get("tool_calls");
        List<ToolCall> toolCalls = null;
        if (toolCallsNode != null && toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            toolCalls = new ArrayList<>();
            for (JsonNode tcNode : toolCallsNode) {
                String id = tcNode.has("id") ? tcNode.get("id").asText() : null;
                String type = tcNode.has("type") ? tcNode.get("type").asText() : "function";
                String fnName = tcNode.at("/function/name").asText("");
                String fnArgs = tcNode.at("/function/arguments").asText("");
                toolCalls.add(new ToolCall(id, type, new ToolCall.FunctionCall(fnName, fnArgs)));
            }
        }

        // Parse content
        JsonNode contentNode = message.get("content");
        String content = null;
        if (contentNode != null && contentNode.isTextual() && !contentNode.asText().isBlank()) {
            content = contentNode.asText();
        }

        return new ChatResponse(content, toolCalls, finishReason);
    }

    // ==================== Raw proxy (pass-through for OpenAI-compatible requests) ====================

    public String proxyRaw(String requestBody) {
        try {
            // Inject model if not present
            JsonNode node = objectMapper.readTree(requestBody);
            if (!node.has("model")) {
                ObjectNode obj = (ObjectNode) node;
                obj.put("model", model);
                requestBody = objectMapper.writeValueAsString(obj);
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            log.error("[LLM-PROXY] Raw proxy failed", e);
            throw new RuntimeException("LLM proxy failed: " + e.getMessage(), e);
        }
    }

    public java.io.InputStream proxyRawStream(String requestBody) {
        try {
            JsonNode node = objectMapper.readTree(requestBody);
            ObjectNode obj = (ObjectNode) node;
            obj.put("stream", true);
            if (!obj.has("model")) {
                obj.put("model", model);
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(obj)))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            return response.body();
        } catch (Exception e) {
            log.error("[LLM-PROXY] Raw stream proxy failed", e);
            throw new RuntimeException("LLM stream proxy failed: " + e.getMessage(), e);
        }
    }
}
