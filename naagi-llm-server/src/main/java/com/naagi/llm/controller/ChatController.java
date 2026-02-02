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
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
@Tag(name = "Chat Completions", description = "OpenAI-compatible chat completion API proxied to llama.cpp")
public class ChatController {

    private final LlamaCppProxy llamaCppProxy;
    private final ObjectMapper objectMapper;

    @PostMapping("/completions")
    @Operation(
            summary = "Create chat completion",
            description = "Send a chat completion request following the OpenAI format. "
                    + "Supports messages array, tools, tool_choice, temperature, max_tokens, and streaming. "
                    + "When stream=true, returns SSE events with delta tokens and a final [DONE] marker."
    )
    @ApiResponse(responseCode = "200", description = "Chat completion response with choices array")
    @ApiResponse(responseCode = "500", description = "LLM request failed")
    public Object chatCompletions(@RequestBody JsonNode requestBody) {
        log.info("[CHAT] Received chat completion request");

        boolean stream = requestBody.has("stream") && requestBody.get("stream").asBoolean(false);

        if (stream) {
            return streamChat(requestBody);
        }

        try {
            ChatRequest chatRequest = parseRequest(requestBody);
            ChatResponse response = llamaCppProxy.chat(chatRequest);
            return ResponseEntity.ok(buildResponseJson(response));
        } catch (Exception e) {
            log.error("[CHAT] Chat completion failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", Map.of("message", e.getMessage(), "type", "server_error")));
        }
    }

    private SseEmitter streamChat(JsonNode requestBody) {
        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.startVirtualThread(() -> {
            try {
                ChatRequest chatRequest = parseRequest(requestBody);
                llamaCppProxy.chatStream(chatRequest, token -> {
                    try {
                        String chunk = objectMapper.writeValueAsString(Map.of(
                                "choices", List.of(Map.of(
                                        "delta", Map.of("content", token),
                                        "index", 0,
                                        "finish_reason", (Object) ""
                                ))
                        ));
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        log.warn("[CHAT] Failed to send stream chunk: {}", e.getMessage());
                    }
                }, () -> {
                    try {
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn("[CHAT] Failed to complete stream: {}", e.getMessage());
                    }
                });
            } catch (Exception e) {
                log.error("[CHAT] Stream failed", e);
                try {
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of("error", e.getMessage()))));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    ChatRequest parseRequest(JsonNode body) throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        if (body.has("messages") && body.get("messages").isArray()) {
            for (JsonNode msg : body.get("messages")) {
                String role = msg.get("role").asText();
                String content = msg.has("content") && !msg.get("content").isNull()
                        ? msg.get("content").asText() : null;
                String name = msg.has("name") ? msg.get("name").asText() : null;
                String toolCallId = msg.has("tool_call_id") ? msg.get("tool_call_id").asText() : null;

                List<ToolCall> toolCalls = null;
                if (msg.has("tool_calls") && msg.get("tool_calls").isArray()) {
                    toolCalls = new ArrayList<>();
                    for (JsonNode tc : msg.get("tool_calls")) {
                        toolCalls.add(new ToolCall(
                                tc.has("id") ? tc.get("id").asText() : null,
                                tc.has("type") ? tc.get("type").asText() : "function",
                                new ToolCall.FunctionCall(
                                        tc.at("/function/name").asText(""),
                                        tc.at("/function/arguments").asText("")
                                )
                        ));
                    }
                }

                messages.add(new ChatMessage(role, content, name, toolCallId, toolCalls));
            }
        }

        double temperature = body.has("temperature") ? body.get("temperature").asDouble(0.7) : 0.7;
        int maxTokens = body.has("max_tokens") ? body.get("max_tokens").asInt(512) : 512;

        List<ToolDefinition> tools = null;
        if (body.has("tools") && body.get("tools").isArray()) {
            tools = new ArrayList<>();
            for (JsonNode toolNode : body.get("tools")) {
                JsonNode fn = toolNode.get("function");
                tools.add(ToolDefinition.function(
                        fn.get("name").asText(),
                        fn.has("description") ? fn.get("description").asText() : "",
                        fn.has("parameters") ? fn.get("parameters") : null
                ));
            }
        }

        String toolChoice = body.has("tool_choice") ? body.get("tool_choice").asText() : null;

        return new ChatRequest(messages, temperature, maxTokens, false, tools, toolChoice);
    }

    Map<String, Object> buildResponseJson(ChatResponse response) {
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
