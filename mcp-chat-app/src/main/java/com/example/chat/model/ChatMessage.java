package com.example.chat.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a single chat message
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String id;
    private String role; // "user" or "assistant"
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private Map<String, Object> metadata; // Tool info, sources, etc.

    public static ChatMessage user(String content) {
        return new ChatMessage(
                java.util.UUID.randomUUID().toString(),
                "user",
                content,
                LocalDateTime.now(),
                null
        );
    }

    public static ChatMessage assistant(String content, Map<String, Object> metadata) {
        return new ChatMessage(
                java.util.UUID.randomUUID().toString(),
                "assistant",
                content,
                LocalDateTime.now(),
                metadata
        );
    }
}
