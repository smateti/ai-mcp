package com.example.chat.controller;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatSession;
import com.example.chat.service.ChatService;
import com.example.chat.service.McpClientService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for chat operations
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final McpClientService mcpClient;

    public ChatController(ChatService chatService, McpClientService mcpClient) {
        this.chatService = chatService;
        this.mcpClient = mcpClient;
    }

    /**
     * Create a new chat session
     */
    @PostMapping("/session")
    public ResponseEntity<SessionResponse> createSession() {
        ChatSession session = chatService.createSession();
        return ResponseEntity.ok(new SessionResponse(
                session.getSessionId(),
                "Session created successfully"
        ));
    }

    /**
     * Send a message and get response
     */
    @PostMapping("/message")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody MessageRequest request) {
        log.info("Received message from session {}: {}", request.sessionId, request.message);

        try {
            ChatMessage response = chatService.processMessage(
                    request.sessionId,
                    request.message
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing message", e);
            return ResponseEntity.ok(ChatMessage.assistant(
                    "❌ Error: " + e.getMessage(),
                    Map.of("error", true)
            ));
        }
    }

    /**
     * Get chat history for a session
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<ChatHistoryResponse> getHistory(@PathVariable String sessionId) {
        ChatSession session = chatService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new ChatHistoryResponse(
                session.getSessionId(),
                session.getMessages(),
                session.getMessageCount()
        ));
    }

    /**
     * List all available MCP tools
     */
    @GetMapping("/tools")
    public ResponseEntity<List<McpClientService.Tool>> listTools() {
        try {
            List<McpClientService.Tool> tools = mcpClient.listTools();
            return ResponseEntity.ok(tools);
        } catch (Exception e) {
            log.error("Error listing tools", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            List<McpClientService.Tool> tools = mcpClient.listTools();
            boolean mcpConnected = tools != null && !tools.isEmpty();

            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "mcpConnected", mcpConnected,
                    "toolsAvailable", tools != null ? tools.size() : 0
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "degraded",
                    "mcpConnected", false,
                    "error", e.getMessage()
            ));
        }
    }

    // DTOs
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageRequest {
        private String sessionId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponse {
        private String sessionId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatHistoryResponse {
        private String sessionId;
        private List<ChatMessage> messages;
        private int totalMessages;
    }
}
