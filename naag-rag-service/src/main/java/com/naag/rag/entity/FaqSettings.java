package com.naag.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Runtime-configurable settings for FAQ functionality.
 * Singleton entity - only one row with id="default"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "faq_settings")
public class FaqSettings {

    @Id
    @Builder.Default
    private String id = "default";

    /**
     * Whether FAQ query is enabled - if true, system will check FAQs first before RAG
     */
    @Builder.Default
    private boolean faqQueryEnabled = true;

    /**
     * Minimum similarity score for FAQ match (0.0 - 1.0)
     */
    @Builder.Default
    private double minSimilarityScore = 0.85;

    /**
     * Whether to auto-store user questions for analytics
     */
    @Builder.Default
    private boolean storeUserQuestions = true;

    /**
     * Auto-select threshold for Q&A validation
     */
    @Builder.Default
    private double autoSelectThreshold = 0.7;

    private LocalDateTime updatedAt;
    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Create default settings instance
     */
    public static FaqSettings createDefault() {
        return FaqSettings.builder()
                .id("default")
                .faqQueryEnabled(true)
                .minSimilarityScore(0.85)
                .storeUserQuestions(true)
                .autoSelectThreshold(0.7)
                .build();
    }
}
