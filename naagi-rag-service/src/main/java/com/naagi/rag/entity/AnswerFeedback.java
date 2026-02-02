package com.naagi.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tracks user feedback (thumbs up/down) on RAG answers.
 * Used for quality evaluation and active learning.
 */
@Entity
@Table(name = "answer_feedback", indexes = {
    @Index(name = "idx_fb_session", columnList = "sessionId"),
    @Index(name = "idx_fb_category", columnList = "categoryId"),
    @Index(name = "idx_fb_rating", columnList = "rating"),
    @Index(name = "idx_fb_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String sessionId;

    @Lob
    @Column(columnDefinition = "CLOB", nullable = false)
    private String question;

    @Lob
    @Column(columnDefinition = "CLOB", nullable = false)
    private String answer;

    /**
     * 1 = thumbs up, -1 = thumbs down
     */
    @Column(nullable = false)
    private int rating;

    /**
     * Optional text feedback from user
     */
    @Lob
    @Column(columnDefinition = "CLOB")
    private String comment;

    @Column
    private String categoryId;

    /**
     * Source of the answer: RAG, FAQ, TOOL, AGENT
     */
    @Column
    private String source;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
