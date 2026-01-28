package com.naagi.chat.controller;

import com.naagi.chat.entity.ChatSessionEntity;
import com.naagi.chat.model.ChatMessage;
import com.naagi.chat.model.ChatSession;
import com.naagi.chat.service.AuditService;
import com.naagi.chat.service.ChatHistoryService;
import com.naagi.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService historyService;
    private final AuditService auditService;
    private final String orchestratorUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Default user ID - in production this would come from authentication
    private static final String DEFAULT_USER_ID = "default-user";

    public ChatController(
            ChatService chatService,
            ChatHistoryService historyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${naagi.services.orchestrator.url:http://localhost:8086}") String orchestratorUrl) {
        this.chatService = chatService;
        this.historyService = historyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.orchestratorUrl = orchestratorUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @GetMapping("/")
    public String index(Model model) {
        String sessionId = UUID.randomUUID().toString();
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("userId", DEFAULT_USER_ID);
        return "chat";
    }

    @GetMapping("/chat/{sessionId}")
    public String chat(@PathVariable String sessionId, Model model) {
        ChatSession session = chatService.getOrCreateSession(sessionId);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("userId", DEFAULT_USER_ID);
        model.addAttribute("messages", session.getMessages());
        return "chat";
    }

    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody Map<String, Object> request,
                                                    HttpServletRequest httpRequest) {
        String sessionId = (String) request.get("sessionId");
        String message = (String) request.get("message");
        String categoryId = (String) request.get("categoryId");
        String categoryName = (String) request.get("categoryName");
        String userId = (String) request.getOrDefault("userId", DEFAULT_USER_ID);

        if (sessionId == null || message == null || message.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Processing message for session {}, user {}, category {}: {}",
                sessionId, userId, categoryId, message.substring(0, Math.min(50, message.length())));

        ChatMessage response = chatService.processMessage(
                sessionId, message, userId, categoryId, categoryName, clientIp, userAgent);

        return ResponseEntity.ok(response);
    }

    /**
     * Streaming chat endpoint - streams response tokens through orchestrator
     * This supports both RAG queries and tool calls
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter streamMessage(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(120000L); // 2 minute timeout

        String sessionId = (String) request.get("sessionId");
        String message = (String) request.get("message");
        String categoryId = (String) request.get("categoryId");
        String categoryName = (String) request.get("categoryName");
        String clientIp = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        long startTime = System.currentTimeMillis();

        // Ensure session exists in history database for sidebar display
        String userId = (String) request.getOrDefault("userId", DEFAULT_USER_ID);
        historyService.getOrCreateSession(sessionId, userId, categoryId, categoryName);

        new Thread(() -> {
            StringBuilder fullResponse = new StringBuilder();
            final String[] selectedTool = {null};
            final String[] llmPrompt = {null};  // Capture LLM prompt for audit
            final boolean[] success = {true};
            final String[] errorMessage = {null};

            try {
                // Build request to orchestrator streaming endpoint
                var orchestratorRequest = objectMapper.createObjectNode();
                orchestratorRequest.put("message", message);
                orchestratorRequest.put("sessionId", sessionId);
                if (categoryId != null) {
                    orchestratorRequest.put("categoryId", categoryId);
                }

                HttpRequest httpReq = HttpRequest.newBuilder()
                        .uri(URI.create(orchestratorUrl + "/api/orchestrate/stream"))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(orchestratorRequest)))
                        .build();

                HttpResponse<java.io.InputStream> response = httpClient.send(httpReq,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    emitter.send(SseEmitter.event().name("error").data("Orchestrator error: HTTP " + response.statusCode()));
                    emitter.complete();
                    success[0] = false;
                    errorMessage[0] = "Orchestrator error: HTTP " + response.statusCode();
                    logStreamingAudit(sessionId, message, fullResponse.toString(), categoryId, categoryName,
                            selectedTool[0], llmPrompt[0], startTime, success[0], errorMessage[0], clientIp, userAgent);
                    return;
                }

                // Forward SSE events from orchestrator to client
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("event:")) {
                            String eventName = line.substring(6).trim();
                            String dataLine = reader.readLine();
                            if (dataLine != null && dataLine.startsWith("data:")) {
                                // Don't trim - preserve JSON data as-is
                                String data = dataLine.substring(5);
                                // Only trim for non-token events
                                if (!"token".equals(eventName)) {
                                    data = data.trim();
                                }
                                emitter.send(SseEmitter.event().name(eventName).data(data));

                                // Collect response for audit
                                if ("token".equals(eventName)) {
                                    try {
                                        // Parse JSON token {"t":"..."}
                                        if (data.contains("\"t\"")) {
                                            var tokenNode = objectMapper.readTree(data.trim());
                                            if (tokenNode.has("t")) {
                                                fullResponse.append(tokenNode.get("t").asText());
                                            }
                                        }
                                    } catch (Exception e) {
                                        // Not JSON, append as-is
                                        fullResponse.append(data);
                                    }
                                } else if ("tool".equals(eventName)) {
                                    try {
                                        var toolNode = objectMapper.readTree(data);
                                        if (toolNode.has("tool")) {
                                            selectedTool[0] = toolNode.get("tool").asText();
                                        }
                                    } catch (Exception e) {
                                        log.debug("Failed to parse tool event", e);
                                    }
                                } else if ("prompt".equals(eventName)) {
                                    // Capture LLM prompt for audit trail (don't forward to client)
                                    try {
                                        var promptNode = objectMapper.readTree(data);
                                        if (promptNode.has("prompt")) {
                                            llmPrompt[0] = promptNode.get("prompt").asText();
                                        }
                                    } catch (Exception e) {
                                        log.debug("Failed to parse prompt event", e);
                                    }
                                }

                                if ("done".equals(eventName)) {
                                    break;
                                }
                            }
                        } else if (line.startsWith("data:")) {
                            // Handle data-only lines - forward as-is
                            String data = line.substring(5);
                            if (!data.trim().isEmpty()) {
                                emitter.send(SseEmitter.event().name("token").data(data));
                                fullResponse.append(data);
                            }
                        }
                    }
                }
                emitter.complete();

            } catch (Exception e) {
                log.error("Streaming error", e);
                success[0] = false;
                errorMessage[0] = e.getMessage();
                try {
                    emitter.send(SseEmitter.event().name("error").data("Error: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                // Log to audit trail
                logStreamingAudit(sessionId, message, fullResponse.toString(), categoryId, categoryName,
                        selectedTool[0], llmPrompt[0], startTime, success[0], errorMessage[0], clientIp, userAgent);
            }
        }).start();

        return emitter;
    }

    private void logStreamingAudit(String sessionId, String userMessage, String assistantResponse,
                                    String categoryId, String categoryName, String selectedTool,
                                    String llmPrompt, long startTime, boolean success, String errorMessage,
                                    String clientIp, String userAgent) {
        try {
            long processingTime = System.currentTimeMillis() - startTime;
            String messageId = UUID.randomUUID().toString();

            // Save messages to chat history for sidebar display
            historyService.addMessage(sessionId, "user", userMessage, Map.of());
            historyService.addMessage(sessionId, "assistant", assistantResponse, Map.of(
                    "selectedTool", selectedTool != null ? selectedTool : "",
                    "processingTimeMs", processingTime,
                    "success", success
            ));

            // Update session title based on first message
            historyService.updateSessionTitle(sessionId, userMessage);

            auditService.logMessageProcessed(
                    DEFAULT_USER_ID, sessionId, messageId,
                    userMessage, assistantResponse,
                    categoryId, categoryName,
                    null, // intent
                    selectedTool,
                    llmPrompt, // LLM prompt sent to model
                    null, // confidence
                    processingTime, success, errorMessage,
                    clientIp, userAgent
            );
            log.debug("Streaming audit logged for session {}", sessionId);

            // Track user question for FAQ analytics (async, non-blocking)
            // Note: Full audit data is already stored above. This only tracks for FAQ deduplication/frequency.
            trackQuestionForFaqAnalytics(userMessage, categoryId);

        } catch (Exception e) {
            log.warn("Failed to log streaming audit: {}", e.getMessage());
        }
    }

    /**
     * Track user question for FAQ analytics (deduplication and frequency).
     * This sends minimal data - full audit is stored separately.
     * Done asynchronously to not block the response.
     */
    private void trackQuestionForFaqAnalytics(String question, String categoryId) {
        new Thread(() -> {
            try {
                String ragServiceUrl = orchestratorUrl.replace(":8086", ":8080");

                var requestBody = objectMapper.createObjectNode();
                requestBody.put("question", question);
                requestBody.put("categoryId", categoryId);

                HttpRequest httpReq = HttpRequest.newBuilder()
                        .uri(URI.create(ragServiceUrl + "/api/questions/track"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                        .build();

                HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    log.debug("Question tracked for FAQ analytics");
                } else {
                    log.debug("Question tracking returned {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.debug("Failed to track question (non-critical): {}", e.getMessage());
            }
        }).start();
    }

    @GetMapping("/api/chat/{sessionId}/history")
    @ResponseBody
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        List<ChatMessage> messages = chatService.getSessionMessages(sessionId);
        return ResponseEntity.ok(messages);
    }

    // ==================== Session Management APIs ====================

    @GetMapping("/api/sessions")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getUserSessions(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String userId) {
        List<ChatSessionEntity> sessions = historyService.getUserSessions(userId);
        List<Map<String, Object>> result = sessions.stream()
                .map(this::sessionToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/sessions/recent")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getRecentSessions(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String userId,
            @RequestParam(defaultValue = "7") int days) {
        List<ChatSessionEntity> sessions = historyService.getRecentSessions(userId, days);
        List<Map<String, Object>> result = sessions.stream()
                .map(this::sessionToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/sessions/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return historyService.getSession(sessionId)
                .map(session -> ResponseEntity.ok(sessionToMapWithMessages(session)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/sessions/{sessionId}/messages")
    @ResponseBody
    public ResponseEntity<List<ChatMessage>> getSessionMessages(@PathVariable String sessionId) {
        List<ChatMessage> messages = chatService.getSessionMessages(sessionId);
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/api/sessions/{sessionId}")
    @ResponseBody
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/sessions/{sessionId}/archive")
    @ResponseBody
    public ResponseEntity<Void> archiveSession(@PathVariable String sessionId) {
        chatService.archiveSession(sessionId);
        return ResponseEntity.ok().build();
    }

    // ==================== Audit Trail APIs ====================

    @GetMapping("/api/audit")
    @ResponseBody
    public ResponseEntity<List<?>> getAuditLogs(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Integer lastMinutes,
            @RequestParam(required = false) Integer lastHours) {
        // If time-based filter is provided, use date range query
        if (lastMinutes != null || lastHours != null) {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start;
            if (lastMinutes != null) {
                start = end.minusMinutes(lastMinutes);
            } else {
                start = end.minusHours(lastHours);
            }
            return ResponseEntity.ok(auditService.getAuditLogsByDateRange(userId, start, end));
        }
        return ResponseEntity.ok(auditService.getAuditLogs(userId, page, size));
    }

    @GetMapping("/api/audit/session/{sessionId}")
    @ResponseBody
    public ResponseEntity<List<?>> getSessionAuditLogs(@PathVariable String sessionId) {
        return ResponseEntity.ok(auditService.getSessionAuditLogs(sessionId));
    }

    @GetMapping("/api/audit/errors")
    @ResponseBody
    public ResponseEntity<List<?>> getFailedOperations(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String userId) {
        return ResponseEntity.ok(auditService.getFailedOperations(userId));
    }

    /**
     * Find the answer from audit trail for a given question.
     * Used to pre-fill the answer when promoting a user question to FAQ.
     */
    @GetMapping("/api/audit/answer-by-question")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAnswerByQuestion(@RequestParam String question) {
        return auditService.findAnswerByQuestion(question)
                .map(audit -> ResponseEntity.ok(Map.<String, Object>of(
                        "found", true,
                        "question", audit.getUserQuestion(),
                        "answer", audit.getAssistantResponse(),
                        "categoryId", audit.getCategoryId() != null ? audit.getCategoryId() : "",
                        "categoryName", audit.getCategoryName() != null ? audit.getCategoryName() : "",
                        "timestamp", audit.getTimestamp().toString()
                )))
                .orElse(ResponseEntity.ok(Map.of(
                        "found", false,
                        "message", "No answer found in audit trail for this question"
                )));
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserStats(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String userId) {
        return ResponseEntity.ok(chatService.getUserStats(userId));
    }

    // ==================== Search APIs ====================

    @GetMapping("/api/search/sessions")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchSessions(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String userId,
            @RequestParam String query) {
        List<ChatSessionEntity> sessions = historyService.searchSessions(userId, query);
        List<Map<String, Object>> result = sessions.stream()
                .map(this::sessionToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/search/messages")
    @ResponseBody
    public ResponseEntity<List<ChatMessage>> searchMessages(
            @RequestParam(defaultValue = DEFAULT_USER_ID) String userId,
            @RequestParam String query) {
        return ResponseEntity.ok(
                historyService.searchMessages(userId, query).stream()
                        .map(historyService::toDto)
                        .collect(Collectors.toList())
        );
    }

    // ==================== Health Check ====================

    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "naagi-chat-app"
        ));
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> sessionToMap(ChatSessionEntity session) {
        return Map.of(
                "id", session.getId(),
                "name", session.getTitle() != null ? session.getTitle() : "New Chat",
                "categoryId", session.getCategoryId() != null ? session.getCategoryId() : "",
                "categoryName", session.getCategoryName() != null ? session.getCategoryName() : "",
                "createdAt", session.getCreatedAt().toString(),
                "updatedAt", session.getLastMessageAt() != null ? session.getLastMessageAt().toString() : session.getCreatedAt().toString(),
                "messageCount", session.getMessageCount(),
                "active", session.isActive()
        );
    }

    private Map<String, Object> sessionToMapWithMessages(ChatSessionEntity session) {
        List<ChatMessage> messages = session.getMessages().stream()
                .map(historyService::toDto)
                .collect(Collectors.toList());

        return Map.of(
                "id", session.getId(),
                "name", session.getTitle() != null ? session.getTitle() : "New Chat",
                "categoryId", session.getCategoryId() != null ? session.getCategoryId() : "",
                "categoryName", session.getCategoryName() != null ? session.getCategoryName() : "",
                "createdAt", session.getCreatedAt().toString(),
                "updatedAt", session.getLastMessageAt() != null ? session.getLastMessageAt().toString() : session.getCreatedAt().toString(),
                "messageCount", session.getMessageCount(),
                "active", session.isActive(),
                "messages", messages
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Take first IP if multiple (X-Forwarded-For can have comma-separated list)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
