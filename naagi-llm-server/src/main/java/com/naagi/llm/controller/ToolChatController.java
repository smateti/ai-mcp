package com.naagi.llm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.llm.model.*;
import com.naagi.llm.service.LlamaCppProxy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/tools")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
@Tag(name = "Tool Calling", description = "Single-shot tool calling - send tools to LLM and get back content or tool_calls")
public class ToolChatController {

    private final LlamaCppProxy llamaCppProxy;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    @Operation(
            summary = "Tool-augmented chat",
            description = "Send messages with tool definitions. The LLM returns either text content or tool_calls. "
                    + "Supports both OpenAI format ({type, function: {...}}) and flat format ({name, description, parameters}). "
                    + "When stream=true with tool_calls, sends a single tool_calls SSE event; with text, streams tokens."
    )
    @ApiResponse(responseCode = "200", description = "Response with content and/or tool_calls in choices array")
    @ApiResponse(responseCode = "500", description = "LLM request failed")
    public Object toolChat(@RequestBody JsonNode requestBody) {
        log.info("[TOOL-CHAT] Received tool chat request");

        boolean stream = requestBody.has("stream") && requestBody.get("stream").asBoolean(false);

        try {
            ChatRequest chatRequest = parseToolRequest(requestBody);

            if (stream) {
                return streamToolChat(chatRequest);
            }

            ChatResponse response = llamaCppProxy.chat(chatRequest);
            return ResponseEntity.ok(buildToolResponse(response));
        } catch (Exception e) {
            log.error("[TOOL-CHAT] Tool chat failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private SseEmitter streamToolChat(ChatRequest chatRequest) {
        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.startVirtualThread(() -> {
            try {
                ChatResponse response = llamaCppProxy.chat(chatRequest);

                if (response.hasToolCalls()) {
                    emitter.send(SseEmitter.event()
                            .name("tool_calls")
                            .data(objectMapper.writeValueAsString(buildToolResponse(response))));
                } else if (response.hasContent()) {
                    String[] words = response.content().split("(?<=\\s)");
                    for (String word : words) {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(objectMapper.writeValueAsString(Map.of("t", word))));
                    }
                }

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(objectMapper.writeValueAsString(Map.of("finish_reason",
                                response.finishReason() != null ? response.finishReason() : "stop"))));
                emitter.complete();
            } catch (Exception e) {
                log.error("[TOOL-CHAT] Stream failed", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(Map.of("message", e.getMessage()))));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    ChatRequest parseToolRequest(JsonNode body) throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        if (body.has("messages") && body.get("messages").isArray()) {
            for (JsonNode msg : body.get("messages")) {
                String role = msg.get("role").asText();
                String content = msg.has("content") && !msg.get("content").isNull()
                        ? msg.get("content").asText() : null;
                String name = msg.has("name") ? msg.get("name").asText() : null;
                String toolCallId = msg.has("tool_call_id") ? msg.get("tool_call_id").asText() : null;
                messages.add(new ChatMessage(role, content, name, toolCallId, null));
            }
        }

        List<ToolDefinition> tools = new ArrayList<>();
        if (body.has("tools") && body.get("tools").isArray()) {
            for (JsonNode toolNode : body.get("tools")) {
                String toolName;
                String description;
                JsonNode parameters;

                if (toolNode.has("function")) {
                    JsonNode fn = toolNode.get("function");
                    toolName = fn.get("name").asText();
                    description = fn.has("description") ? fn.get("description").asText() : "";
                    parameters = fn.has("parameters") ? fn.get("parameters") : null;
                } else {
                    toolName = toolNode.get("name").asText();
                    description = toolNode.has("description") ? toolNode.get("description").asText() : "";
                    parameters = toolNode.has("parameters") ? toolNode.get("parameters") : null;
                }

                tools.add(ToolDefinition.function(toolName, description, parameters));
            }
        }

        double temperature = body.has("temperature") ? body.get("temperature").asDouble(0.2) : 0.2;
        int maxTokens = body.has("maxTokens") ? body.get("maxTokens").asInt(1024) : 1024;
        String toolChoice = body.has("toolChoice") ? body.get("toolChoice").asText("auto") : "auto";

        return new ChatRequest(messages, temperature, maxTokens, false, tools, toolChoice);
    }

    Map<String, Object> buildToolResponse(ChatResponse response) {
        var message = new java.util.LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        if (response.content() != null) {
            message.put("content", response.content());
        }
        if (response.hasToolCalls()) {
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (ToolCall tc : response.toolCalls()) {
                toolCalls.add(Map.of(
                        "id", tc.id() != null ? tc.id() : "",
                        "type", tc.type(),
                        "function", Map.of(
                                "name", tc.function().name(),
                                "arguments", tc.function().arguments()
                        )
                ));
            }
            message.put("tool_calls", toolCalls);
        }

        return Map.of(
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", message,
                        "finish_reason", response.finishReason() != null ? response.finishReason() : "stop"
                ))
        );
    }
}
