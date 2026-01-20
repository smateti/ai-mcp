package com.example.userchat.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_session_id", columnList = "session_id")
})
@Data
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String role; // "user" or "assistant"

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private String toolUsed;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ChatMessage(String sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

    public ChatMessage(String sessionId, String role, String content, String toolUsed) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.toolUsed = toolUsed;
    }
}
