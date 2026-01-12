package com.example.chat.controller;

import com.example.chat.llm.ChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for streaming LLM responses via SSE
 */
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Slf4j
public class StreamingChatController {

    private final ChatClient chatClient;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /**
     * Stream LLM response token by token
     *
     * Example: GET /api/stream/chat?prompt=Tell+me+about+machine+learning
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String prompt,
            @RequestParam(defaultValue = "0.7") double temperature,
            @RequestParam(defaultValue = "500") int maxTokens) {

        log.info("Starting LLM streaming for prompt: {}", prompt.substring(0, Math.min(50, prompt.length())));

        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout
        String emitterId = "chat_" + System.currentTimeMillis();
        activeEmitters.put(emitterId, emitter);

        emitter.onCompletion(() -> {
            log.info("Stream completed: {}", emitterId);
            activeEmitters.remove(emitterId);
        });

        emitter.onTimeout(() -> {
            log.warn("Stream timed out: {}", emitterId);
            activeEmitters.remove(emitterId);
        });

        emitter.onError(e -> {
            log.error("Stream error: {}", emitterId, e);
            activeEmitters.remove(emitterId);
        });

        // Stream LLM response asynchronously
        executorService.execute(() -> streamLlmResponse(emitter, prompt, temperature, maxTokens));

        return emitter;
    }

    /**
     * Stream LLM response token by token
     */
    private void streamLlmResponse(SseEmitter emitter, String prompt, double temperature, int maxTokens) {
        try {
            // Send start event
            sendEvent(emitter, "start", Map.of(
                "message", "Starting LLM generation...",
                "prompt", prompt.substring(0, Math.min(100, prompt.length()))
            ));

            StringBuilder fullResponse = new StringBuilder();

            // Stream tokens
            chatClient.chatStream(prompt, temperature, maxTokens, token -> {
                try {
                    fullResponse.append(token);

                    // Send token event
                    sendEvent(emitter, "token", Map.of(
                        "text", token,
                        "currentLength", fullResponse.length()
                    ));

                } catch (IOException e) {
                    log.error("Error sending token", e);
                    throw new RuntimeException(e);
                }
            });

            // Send completion event
            sendEvent(emitter, "complete", Map.of(
                "fullResponse", fullResponse.toString(),
                "totalTokens", fullResponse.length()
            ));

            emitter.complete();
            log.info("LLM streaming completed successfully");

        } catch (Exception e) {
            log.error("Error during LLM streaming", e);
            try {
                sendEvent(emitter, "error", Map.of(
                    "message", e.getMessage()
                ));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                log.error("Error sending error event", ioException);
            }
        }
    }

    /**
     * Send SSE event
     */
    private void sendEvent(SseEmitter emitter, String eventType, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventType)
                .data(data)
                .id(String.valueOf(System.currentTimeMillis()))
        );
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "healthy",
            "activeStreams", activeEmitters.size()
        );
    }

    /**
     * Get active streams
     */
    @GetMapping("/connections")
    public Map<String, Object> getConnections() {
        return Map.of(
            "count", activeEmitters.size(),
            "streams", activeEmitters.keySet()
        );
    }
}
