package com.naagi.rag.llm.llamacpp;

import com.naagi.rag.http.Http;
import com.naagi.rag.json.Json;
import com.naagi.rag.llm.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI-compatible chat client for llama.cpp and Ollama servers.
 * Implements the standardized ChatClient interface with structured messages and tool support.
 */
public final class LlamaCppOpenAIChatClient implements ChatClient {
    private static final Logger log = LoggerFactory.getLogger(LlamaCppOpenAIChatClient.class);

    private final String baseUrl;
    private final String model;

    public LlamaCppOpenAIChatClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            long buildStart = System.currentTimeMillis();
            ObjectNode body = buildRequestBody(request);

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
            ChatResponse chatResponse = parseResponse(resp.body());
            long parseTime = System.currentTimeMillis() - parseStart;

            long totalTime = System.currentTimeMillis() - startTime;
            log.debug("[CHAT TIMING] total={}ms (build={}ms, http={}ms, parse={}ms) maxTokens={}",
                    totalTime, buildTime, httpTime, parseTime, request.maxTokens());

            return chatResponse;
        } catch (Exception e) {
            throw new RuntimeException("llama.cpp OpenAI chat failed", e);
        }
    }

    @Override
    public void chatStream(ChatRequest request, Consumer<String> onToken) {
        try {
            // Ensure stream flag is set
            ChatRequest streamReq = new ChatRequest(
                    request.messages(), request.temperature(), request.maxTokens(),
                    true, request.tools(), request.toolChoice());

            ObjectNode body = buildRequestBody(streamReq);

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

    // ==================== Request Building ====================

    private ObjectNode buildRequestBody(ChatRequest request) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        body.put("stream", request.stream());

        // Tool choice: only send when tools are present or explicitly specified
        if (request.toolChoice() != null) {
            body.put("tool_choice", request.toolChoice());
        } else if (!request.hasTools()) {
            // No tools — don't send tool_choice at all (some servers mishandle it)
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
            // Include tool_calls for assistant messages that triggered tool calls
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

        // Tools array (OpenAI function calling format)
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

    private ChatResponse parseResponse(String responseBody) throws Exception {
        JsonNode root = Json.MAPPER.readTree(responseBody);
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
}
