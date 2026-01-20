package com.example.chat.controller;

import com.example.chat.llm.ChatClient;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatSession;
import com.example.chat.service.ChatService;
import com.example.chat.service.LlmToolSelectionService;
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
    private final ChatClient chatClient;
    private final LlmToolSelectionService toolSelector;

    public ChatController(ChatService chatService, McpClientService mcpClient, ChatClient chatClient, LlmToolSelectionService toolSelector) {
        this.chatService = chatService;
        this.mcpClient = mcpClient;
        this.chatClient = chatClient;
        this.toolSelector = toolSelector;
    }

    /**
     * Debug endpoint to check ChatClient configuration
     */
    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug() {
        return ResponseEntity.ok(Map.of(
            "chatClientClass", chatClient.getClass().getSimpleName(),
            "chatClientFullClass", chatClient.getClass().getName()
        ));
    }

    /**
     * Debug endpoint to test LLM directly
     */
    @GetMapping("/debug/llm")
    public ResponseEntity<Map<String, Object>> debugLlm(@RequestParam(defaultValue = "Say hello") String prompt) {
        try {
            String response = chatClient.chatOnce(prompt, 0.2, 256);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "prompt", prompt,
                "response", response
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getClass().getName() + ": " + e.getMessage()
            ));
        }
    }

    /**
     * Debug endpoint to test tool selection service directly
     */
    @GetMapping("/debug/tool-selection")
    public ResponseEntity<Map<String, Object>> debugToolSelection(@RequestParam(defaultValue = "add 5 and 10") String message) {
        try {
            List<McpClientService.Tool> tools = mcpClient.listTools();
            LlmToolSelectionService.ToolSelectionResult result = toolSelector.selectTool(message, tools);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "toolCount", tools.size(),
                "toolSelectorChatClient", toolSelector.getChatClientClassName(),
                "controllerChatClient", chatClient.getClass().getName(),
                "selectedTool", result.selectedTool() != null ? result.selectedTool() : "null",
                "confidence", result.confidence(),
                "reasoning", result.reasoning() != null ? result.reasoning() : "",
                "parameters", result.extractedParameters()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getClass().getName() + ": " + e.getMessage()
            ));
        }
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
        log.info("Received message from session {} (category: {}): {}",
                request.sessionId, request.categoryId, request.message);

        try {
            ChatMessage response = chatService.processMessage(
                    request.sessionId,
                    request.message,
                    request.categoryId
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
        private String categoryId; // Category for filtering enabled tools
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
