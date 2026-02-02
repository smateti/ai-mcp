package com.naagi.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String id;
    private String role; // "user" or "assistant"
    private String content;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
    private String replyToMessageId;

    // Backward-compatible constructor (existing callers use 5-arg)
    public ChatMessage(String id, String role, String content, LocalDateTime timestamp, Map<String, Object> metadata) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }
}
