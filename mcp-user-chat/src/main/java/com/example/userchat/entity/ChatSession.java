package com.example.userchat.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions")
@Data
@NoArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String categoryId;

    @Column(nullable = false)
    private String categoryName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 1000)
    private String firstQuestion;

    private Integer messageCount = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public ChatSession(String categoryId, String categoryName, String firstQuestion) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.firstQuestion = firstQuestion;
        this.name = "New Chat"; // Will be updated by LLM
        this.messageCount = 0;
    }
}
