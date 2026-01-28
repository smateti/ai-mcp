package com.naagi.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to track unique user questions for FAQ analytics.
 * This is NOT for audit - audit logs are stored separately in chat-app.
 *
 * This entity focuses on:
 * - Deduplicating similar questions (via Qdrant embeddings)
 * - Tracking question frequency (how often similar questions are asked)
 * - Matching questions to existing FAQs
 * - Promoting frequent questions to FAQ candidates
 */
@Entity
@Table(name = "user_questions", indexes = {
    @Index(name = "idx_uq_category", columnList = "categoryId"),
    @Index(name = "idx_uq_matched_faq", columnList = "matchedFaqId"),
    @Index(name = "idx_uq_frequency", columnList = "frequency"),
    @Index(name = "idx_uq_first_asked", columnList = "firstAskedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * The canonical form of the question (first occurrence or representative text)
     */
    @Lob
    @Column(columnDefinition = "CLOB", nullable = false)
    private String question;

    /**
     * Category this question belongs to (for filtering)
     */
    @Column
    private String categoryId;

    /**
     * ID of FAQ that matches this question (if any)
     */
    @Column
    private String matchedFaqId;

    /**
     * Similarity score when matched to FAQ
     */
    @Column
    private Double matchedFaqScore;

    /**
     * How many times this (or similar) question has been asked
     */
    @Builder.Default
    private Integer frequency = 1;

    /**
     * Qdrant point ID for semantic similarity searches
     */
    @Column
    private String qdrantPointId;

    /**
     * When this question was first asked
     */
    @Column(nullable = false)
    private LocalDateTime firstAskedAt;

    /**
     * When this question was last asked
     */
    @Column
    private LocalDateTime lastAskedAt;

    /**
     * Whether this has been promoted to FAQ candidate
     */
    @Builder.Default
    private boolean promotedToFaq = false;

    private LocalDateTime promotedAt;
    private String promotedBy;

    @PrePersist
    protected void onCreate() {
        if (firstAskedAt == null) {
            firstAskedAt = LocalDateTime.now();
        }
        lastAskedAt = LocalDateTime.now();
        if (frequency == null) {
            frequency = 1;
        }
    }

    public void incrementFrequency() {
        this.frequency = (this.frequency == null ? 1 : this.frequency) + 1;
        this.lastAskedAt = LocalDateTime.now();
    }
}
