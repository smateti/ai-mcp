package com.naagi.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    private String sessionId;
    private List<ChatMessage> messages;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;

    // For backwards compatibility - default constructor behavior
    public static ChatSession createNew() {
        ChatSession session = new ChatSession();
        session.sessionId = UUID.randomUUID().toString();
        session.messages = new ArrayList<>();
        session.createdAt = LocalDateTime.now();
        session.lastMessageAt = LocalDateTime.now();
        return session;
    }

    public void addMessage(ChatMessage message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        lastMessageAt = LocalDateTime.now();
    }

    public List<ChatMessage> getMessages() {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        return messages;
    }
}
