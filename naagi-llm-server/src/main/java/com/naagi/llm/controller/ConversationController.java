package com.naagi.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.llm.entity.Conversation;
import com.naagi.llm.model.*;
import com.naagi.llm.service.ConversationService;
import com.naagi.llm.service.LlamaCppProxy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/conversations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
@Tag(name = "Conversational Chat", description = "Server-managed multi-turn conversations with history persistence")
public class ConversationController {

    private final ConversationService conversationService;
    private final LlamaCppProxy llamaCppProxy;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    @Operation(
            summary = "Conversational chat",
            description = "Send a message in a conversation. Omit conversationId to start a new conversation. "
                    + "The server stores full history so callers only send the latest message. "
                    + "When stream=true, returns SSE token events and a done event with conversationId."
    )
    @ApiResponse(responseCode = "200", description = "Conversation response with conversationId, message, and turnNumber")
    @ApiResponse(responseCode = "404", description = "Conversation not found")
    @ApiResponse(responseCode = "500", description = "LLM request failed")
    public Object chat(@RequestBody ConversationRequest request) {
        log.info("[CONVERSATION] Chat request, conversationId={}", request.conversationId());

        if (request.stream()) {
            return streamChat(request);
        }

        try {
            ConversationResponse response = conversationService.chat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[CONVERSATION] Chat failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private SseEmitter streamChat(ConversationRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.startVirtualThread(() -> {
            try {
                Conversation conversation = conversationService.resolveConversationForStream(request);
                List<ChatMessage> messages = conversationService.buildMessages(conversation);

                double temperature = request.temperature() != null ? request.temperature() : 0.7;
                int maxTokens = request.maxTokens() != null ? request.maxTokens() : 512;

                ChatRequest chatRequest = ChatRequest.of(messages, temperature, maxTokens);

                StringBuilder fullResponse = new StringBuilder();
                llamaCppProxy.chatStream(chatRequest, token -> {
                    fullResponse.append(token);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(objectMapper.writeValueAsString(Map.of("t", token))));
                    } catch (Exception e) {
                        log.warn("[CONVERSATION] Failed to stream token: {}", e.getMessage());
                    }
                }, () -> {
                    String assistantMessage = fullResponse.toString();
                    conversationService.saveAssistantTurn(conversation.getId(), assistantMessage);
                    int turnNumber = (conversation.getTurnCount() + 1) / 2;

                    try {
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data(objectMapper.writeValueAsString(Map.of(
                                        "conversationId", conversation.getId(),
                                        "turnNumber", turnNumber
                                ))));
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn("[CONVERSATION] Failed to complete stream: {}", e.getMessage());
                    }
                });
            } catch (Exception e) {
                log.error("[CONVERSATION] Stream failed", e);
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
}
