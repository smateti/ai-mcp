package com.naag.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "faq_entries", indexes = {
    @Index(name = "idx_faq_category", columnList = "categoryId"),
    @Index(name = "idx_faq_doc", columnList = "docId"),
    @Index(name = "idx_faq_active", columnList = "active")
})
public class FaqEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String categoryId;

    @Column(nullable = false)
    private String docId;

    private String uploadId;

    @Column(nullable = false)
    private String questionType; // FINE_GRAIN or SUMMARY

    @Column(columnDefinition = "CLOB", nullable = false)
    private String question;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String answer;

    private Double similarityScore;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastAccessedAt;

    @Builder.Default
    private int accessCount = 0;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void incrementAccessCount() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }
}
