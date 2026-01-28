package com.naagi.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_uploads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUpload {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String docId;

    @Column(length = 500)
    private String title;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String originalContent;

    private String categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus status;

    private int totalChunks;

    private int questionsGenerated;

    private int questionsValidated;

    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processingStartedAt;

    private LocalDateTime processingCompletedAt;

    private LocalDateTime movedToRagAt;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String extractedLinks; // JSON array of extracted URLs from the document

    private String sourceUrl; // Original URL if document was fetched from web

    public enum ProcessingStatus {
        PENDING,
        GENERATING_QA,
        CHUNKING_TEMP,
        VALIDATING_QA,
        READY_FOR_REVIEW,
        APPROVED,
        MOVED_TO_RAG,
        FAILED,
        DELETED
    }
}
