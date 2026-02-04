package com.naagi.orchestrator.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naagi.orchestrator.metrics.OrchestratorMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible LLM client for llama.cpp and Ollama servers.
 * Implements the standardized LlmClient interface with structured messages and tool support.
 */
@Component
@Slf4j
public class LlamaCppClient implements LlmClient {

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OrchestratorMetrics metrics;

    public LlamaCppClient(
            @Value("${naagi.llm.baseUrl}") String baseUrl,
            @Value("${naagi.llm.model}") String model,
            ObjectMapper objectMapper,
            OrchestratorMetrics metrics) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            ObjectNode body = buildRequestBody(request);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("LLM HTTP " + response.statusCode() + ": " + response.body());
            }

            long llmTime = System.currentTimeMillis() - startTime;
            metrics.recordLlmChatTime(llmTime);
            log.debug("[TIMING] LLM chat: {}ms", llmTime);

            return parseResponse(response.body());
        } catch (Exception e) {
            log.error("LLM chat failed", e);
            throw new RuntimeException("LLM chat failed", e);
        }
    }

    // ==================== Request Building ====================

    private ObjectNode buildRequestBody(ChatRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        body.put("stream", request.stream());

        // Tool choice: default to "none" if no tools, otherwise use request value or "auto"
        if (request.toolChoice() != null) {
            body.put("tool_choice", request.toolChoice());
        } else if (!request.hasTools()) {
            // No tools — don't send tool_choice at all (some servers don't support it)
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

        // GBNF grammar constraint (llama.cpp specific — forces output format)
        if (request.hasGrammar()) {
            body.put("grammar", request.grammar());
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
}
