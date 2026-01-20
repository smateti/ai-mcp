package com.naag.chat.model;

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
}
