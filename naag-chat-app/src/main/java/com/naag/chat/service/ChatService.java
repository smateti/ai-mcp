package com.naag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.naag.chat.entity.ChatSessionEntity;
import com.naag.chat.metrics.ChatMetrics;
import com.naag.chat.model.ChatMessage;
import com.naag.chat.model.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final OrchestratorClient orchestratorClient;
    private final ChatHistoryService historyService;
    private final AuditService auditService;
    private final ChatMetrics metrics;

    // In-memory cache for active sessions (for quick access)
    private final Map<String, ChatSession> sessionCache = new ConcurrentHashMap<>();

    public ChatSession getOrCreateSession(String sessionId) {
        return getOrCreateSession(sessionId, "anonymous", null, null);
    }

    public ChatSession getOrCreateSession(String sessionId, String userId, String categoryId, String categoryName) {
        // Check cache first
        ChatSession cachedSession = sessionCache.get(sessionId);
        if (cachedSession != null) {
            return cachedSession;
        }

        // Try to load from database
        return historyService.getSession(sessionId)
                .map(entity -> {
                    ChatSession session = historyService.toDto(entity);
                    sessionCache.put(sessionId, session);
                    return session;
                })
                .orElseGet(() -> {
                    // Create new session
                    ChatSession newSession = new ChatSession();
                    newSession.setSessionId(sessionId);

                    // Persist to database
                    historyService.createSession(sessionId, userId, categoryId, categoryName);

                    sessionCache.put(sessionId, newSession);
                    metrics.recordSessionCreated();
                    return newSession;
                });
    }

    public ChatMessage processMessage(String sessionId, String userMessage) {
        return processMessage(sessionId, userMessage, "anonymous", null, null, null, null);
    }

    public ChatMessage processMessage(String sessionId, String userMessage, String userId,
                                       String categoryId, String categoryName,
                                       String clientIp, String userAgent) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();

        ChatSession session = getOrCreateSession(sessionId, userId, categoryId, categoryName);

        // Add user message to in-memory session
        ChatMessage userMsg = new ChatMessage(
                messageId,
                "user",
                userMessage,
                LocalDateTime.now(),
                Map.of()
        );
        session.addMessage(userMsg);

        // Persist user message
        historyService.addMessage(sessionId, "user", userMessage, Map.of());

        // Call orchestrator
        JsonNode orchestratorResponse;
        String responseContent;
        Map<String, Object> metadata;
        boolean success = true;
        String errorMessage = null;

        try {
            long orchestratorStart = System.currentTimeMillis();
            orchestratorResponse = orchestratorClient.orchestrate(userMessage, sessionId, categoryId);
            long orchestratorTime = System.currentTimeMillis() - orchestratorStart;
            metrics.recordOrchestratorCallTime(orchestratorTime);

            responseContent = extractResponseContent(orchestratorResponse);
            metadata = extractMetadata(orchestratorResponse);
        } catch (Exception e) {
            log.error("Error calling orchestrator", e);
            responseContent = "I'm sorry, I encountered an error processing your request. Please try again.";
            metadata = Map.of();
            success = false;
            errorMessage = e.getMessage();
            metrics.recordMessageError();

            // Log error to audit trail
            auditService.logError(userId, sessionId, "ORCHESTRATOR_ERROR", e.getMessage(),
                    e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : null,
                    clientIp, userAgent);
        }

        long processingTime = System.currentTimeMillis() - startTime;
        String assistantMsgId = UUID.randomUUID().toString();

        // Add processing time to metadata
        Map<String, Object> fullMetadata = new ConcurrentHashMap<>(metadata);
        fullMetadata.put("processingTimeMs", processingTime);
        fullMetadata.put("success", success);
        if (errorMessage != null) {
            fullMetadata.put("errorMessage", errorMessage);
        }

        // Create assistant message
        ChatMessage assistantMsg = new ChatMessage(
                assistantMsgId,
                "assistant",
                responseContent,
                LocalDateTime.now(),
                fullMetadata
        );
        session.addMessage(assistantMsg);

        // Persist assistant message
        historyService.addMessage(sessionId, "assistant", responseContent, fullMetadata);

        // Log to audit trail
        auditService.logMessageProcessed(
                userId, sessionId, messageId,
                userMessage, responseContent,
                categoryId, categoryName,
                (String) metadata.get("intent"),
                (String) metadata.get("selectedTool"),
                (Double) metadata.get("confidence"),
                processingTime, success, errorMessage,
                clientIp, userAgent
        );

        metrics.recordMessageProcessingTime(processingTime);
        log.info("Processed message for session {} in {}ms (success: {})", sessionId, processingTime, success);

        return assistantMsg;
    }

    private String extractResponseContent(JsonNode response) {
        if (response.has("response")) {
            return response.get("response").asText();
        }
        if (response.has("error")) {
            return "Error: " + response.get("error").asText();
        }
        return response.toPrettyString();
    }

    private Map<String, Object> extractMetadata(JsonNode response) {
        Map<String, Object> metadata = new ConcurrentHashMap<>();

        if (response.has("intent")) {
            metadata.put("intent", response.get("intent").asText());
        }
        if (response.has("selectedTool")) {
            metadata.put("selectedTool", response.get("selectedTool").asText());
        }
        if (response.has("confidence")) {
            metadata.put("confidence", response.get("confidence").asDouble());
        }
        if (response.has("requiresConfirmation")) {
            metadata.put("requiresConfirmation", response.get("requiresConfirmation").asBoolean());
        }
        if (response.has("source")) {
            metadata.put("source", response.get("source").asText());
        }

        return metadata;
    }

    // Session management methods
    public List<ChatSession> getUserSessions(String userId) {
        return historyService.getUserSessions(userId).stream()
                .map(this::toSessionSummary)
                .collect(Collectors.toList());
    }

    public List<ChatSession> getRecentSessions(String userId, int days) {
        return historyService.getRecentSessions(userId, days).stream()
                .map(this::toSessionSummary)
                .collect(Collectors.toList());
    }

    public void deleteSession(String sessionId) {
        sessionCache.remove(sessionId);
        historyService.deleteSession(sessionId);
        metrics.recordSessionDeleted();
    }

    public void archiveSession(String sessionId) {
        historyService.archiveSession(sessionId);
    }

    private ChatSession toSessionSummary(ChatSessionEntity entity) {
        ChatSession session = new ChatSession();
        session.setSessionId(entity.getId());
        session.setCreatedAt(entity.getCreatedAt());
        session.setLastMessageAt(entity.getLastMessageAt());
        // Don't load all messages for summary - they can be fetched separately
        return session;
    }

    // Get session messages (for loading chat history)
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return historyService.getSessionMessages(sessionId).stream()
                .map(historyService::toDto)
                .collect(Collectors.toList());
    }

    // Clear cache for a session (useful when reloading from DB)
    public void invalidateCache(String sessionId) {
        sessionCache.remove(sessionId);
    }

    // Get statistics for a user
    public Map<String, Object> getUserStats(String userId) {
        return Map.of(
                "sessionCount", historyService.getSessionCount(userId),
                "messageCount", historyService.getMessageCount(userId),
                "auditStats", auditService.getUserStats(userId)
        );
    }
}
