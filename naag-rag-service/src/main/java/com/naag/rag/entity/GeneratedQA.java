package com.naag.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_qa")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedQA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String uploadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    @Lob
    @Column(columnDefinition = "CLOB", nullable = false)
    private String question;

    @Lob
    @Column(columnDefinition = "CLOB", nullable = false)
    private String expectedAnswer;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String ragAnswer;

    private Double similarityScore;

    @Enumerated(EnumType.STRING)
    private ValidationStatus validationStatus;

    private LocalDateTime generatedAt;

    private LocalDateTime validatedAt;

    // FAQ Management fields
    @Lob
    @Column(columnDefinition = "CLOB")
    private String editedAnswer;        // Admin-edited answer (null = use original)

    @Enumerated(EnumType.STRING)
    private AnswerSource selectedAnswerSource;  // Which answer to use for FAQ

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean selectedForFaq = false;     // Admin selected this Q&A for FAQ

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FaqStatus faqStatus = FaqStatus.PENDING;  // FAQ approval status

    private LocalDateTime faqApprovedAt;

    private String faqApprovedBy;

    private String faqQdrantPointId;    // Qdrant point ID after FAQ approval

    public enum QuestionType {
        FINE_GRAIN,
        SUMMARY
    }

    public enum ValidationStatus {
        PENDING,
        PASSED,
        FAILED,
        SKIPPED
    }

    public enum FaqStatus {
        PENDING,      // Not yet reviewed for FAQ
        APPROVED,     // Approved and stored in FAQ collection
        REJECTED      // Explicitly rejected
    }

    public enum AnswerSource {
        EXPECTED,     // Use expectedAnswer
        RAG,          // Use ragAnswer
        EDITED        // Use editedAnswer
    }

    /**
     * Get the final answer based on selected source
     */
    public String getFinalAnswer() {
        if (selectedAnswerSource == null) {
            return expectedAnswer;  // Default to expected
        }
        return switch (selectedAnswerSource) {
            case EXPECTED -> expectedAnswer;
            case RAG -> ragAnswer;
            case EDITED -> editedAnswer != null ? editedAnswer : expectedAnswer;
        };
    }
}
