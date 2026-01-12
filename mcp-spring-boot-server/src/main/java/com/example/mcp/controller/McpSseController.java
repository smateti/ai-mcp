package com.example.mcp.controller;

import com.example.mcp.model.Tool;
import com.example.mcp.service.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE (Server-Sent Events) Controller for real-time streaming of tool execution
 */
@Controller
@RequestMapping("/mcp/stream")
@RequiredArgsConstructor
@Slf4j
public class McpSseController {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // Store active SSE connections
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /**
     * Execute a tool with streaming updates via SSE
     *
     * Example usage:
     * GET /mcp/stream/execute/rag_query?question=what+is+machine+learning
     *
     * The client receives real-time updates as the tool executes.
     */
    @GetMapping(value = "/execute/{toolName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter executeToolStreaming(
            @PathVariable String toolName,
            @RequestParam Map<String, String> params) {

        log.info("Starting SSE streaming for tool: {} with params: {}", toolName, params);

        // Create SSE emitter with 5 minute timeout
        SseEmitter emitter = new SseEmitter(300_000L);
        String emitterId = toolName + "_" + System.currentTimeMillis();
        activeEmitters.put(emitterId, emitter);

        // Cleanup on completion or timeout
        emitter.onCompletion(() -> {
            log.info("SSE stream completed for: {}", emitterId);
            activeEmitters.remove(emitterId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE stream timed out for: {}", emitterId);
            activeEmitters.remove(emitterId);
        });

        emitter.onError(e -> {
            log.error("SSE stream error for: {}", emitterId, e);
            activeEmitters.remove(emitterId);
        });

        // Execute tool asynchronously and stream updates
        executorService.execute(() -> executeToolWithStreaming(emitter, toolName, params));

        return emitter;
    }

    /**
     * Execute tool and send progress updates via SSE
     */
    private void executeToolWithStreaming(SseEmitter emitter, String toolName, Map<String, String> params) {
        try {
            // Send initial status
            sendEvent(emitter, "status", Map.of(
                "stage", "starting",
                "message", "Initializing tool execution...",
                "timestamp", Instant.now().toString()
            ));

            // Find the tool
            Tool tool = toolRegistry.getAllTools().stream()
                    .filter(t -> t.getName().equals(toolName))
                    .findFirst()
                    .orElse(null);

            if (tool == null) {
                sendEvent(emitter, "error", Map.of(
                    "message", "Tool not found: " + toolName,
                    "timestamp", Instant.now().toString()
                ));
                emitter.complete();
                return;
            }

            // Send validation status
            sendEvent(emitter, "status", Map.of(
                "stage", "validating",
                "message", "Validating parameters...",
                "timestamp", Instant.now().toString()
            ));

            // Convert String params to JsonNode for execution
            JsonNode jsonParams = objectMapper.valueToTree(params);

            // Send execution status
            sendEvent(emitter, "status", Map.of(
                "stage", "executing",
                "message", "Executing tool: " + toolName,
                "tool", toolName,
                "timestamp", Instant.now().toString()
            ));

            // Execute the tool using ToolRegistry
            Object result = toolRegistry.executeTool(toolName, jsonParams);

            // For RAG queries, we could stream the LLM response token by token
            // For now, we send the complete result
            sendEvent(emitter, "status", Map.of(
                "stage", "processing",
                "message", "Processing results...",
                "timestamp", Instant.now().toString()
            ));

            // Send the final result
            sendEvent(emitter, "result", Map.of(
                "tool", toolName,
                "data", result,
                "success", true,
                "timestamp", Instant.now().toString()
            ));

            // Send completion
            sendEvent(emitter, "status", Map.of(
                "stage", "complete",
                "message", "Tool execution completed successfully",
                "timestamp", Instant.now().toString()
            ));

            emitter.complete();
            log.info("SSE streaming completed successfully for tool: {}", toolName);

        } catch (Exception e) {
            log.error("Error during tool execution streaming", e);
            try {
                sendEvent(emitter, "error", Map.of(
                    "message", e.getMessage(),
                    "tool", toolName,
                    "timestamp", Instant.now().toString()
                ));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                log.error("Error sending error event", ioException);
            }
        }
    }

    /**
     * Send SSE event to client
     */
    private void sendEvent(SseEmitter emitter, String eventType, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventType)
                .data(objectMapper.writeValueAsString(data))
                .id(String.valueOf(System.currentTimeMillis()))
        );

        // Small delay to simulate streaming and make updates visible
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get list of active SSE connections
     */
    @GetMapping("/connections")
    @ResponseBody
    public Map<String, Object> getActiveConnections() {
        return Map.of(
            "count", activeEmitters.size(),
            "connections", activeEmitters.keySet()
        );
    }

    /**
     * Health check for SSE endpoint
     */
    @GetMapping("/health")
    @ResponseBody
    public Map<String, Object> health() {
        return Map.of(
            "status", "healthy",
            "activeConnections", activeEmitters.size(),
            "timestamp", Instant.now().toString()
        );
    }

    /**
     * Serve SSE demo page
     */
    @GetMapping("/demo")
    public String showDemoPage() {
        return "mcp/sse-demo";
    }
}
