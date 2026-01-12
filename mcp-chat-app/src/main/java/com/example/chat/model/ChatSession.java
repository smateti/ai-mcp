package com.example.chat.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a chat session with history
 */
@Data
public class ChatSession {
    private final String sessionId;
    private final List<ChatMessage> messages;
    private final LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private PendingToolExecution pendingExecution;

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.lastMessageAt = LocalDateTime.now();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        this.lastMessageAt = LocalDateTime.now();
    }

    public int getMessageCount() {
        return messages.size();
    }

    public void setPendingExecution(String tool, Map<String, Object> params, String state, int timeoutMinutes) {
        this.pendingExecution = new PendingToolExecution();
        this.pendingExecution.tool = tool;
        this.pendingExecution.parameters = params;
        this.pendingExecution.state = state;
        this.pendingExecution.expiresAt = LocalDateTime.now().plusMinutes(timeoutMinutes);
    }

    public void clearPendingExecution() {
        this.pendingExecution = null;
    }

    /**
     * Represents a pending tool execution awaiting user input
     */
    @Data
    public static class PendingToolExecution {
        private String tool;
        private Map<String, Object> parameters;
        private String state; // "awaiting_confirmation", "awaiting_parameters"
        private LocalDateTime expiresAt;

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
}
