package com.example.categoryadmin.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Links RAG documents to categories for scoped document search
 */
@Entity
@Table(name = "category_documents",
       uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "document_id"}))
@Data
@NoArgsConstructor
public class CategoryDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @NotBlank(message = "Document ID is required")
    @Column(name = "document_id", nullable = false, length = 100)
    private String documentId;

    @Column(name = "document_name", length = 200)
    private String documentName;

    @Column(name = "document_description", length = 500)
    private String documentDescription;

    @Column(name = "document_type", length = 50)
    private String documentType; // e.g., "pdf", "markdown", "text", etc.

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }

    public CategoryDocument(Category category, String documentId, String documentName, String documentDescription) {
        this.category = category;
        this.documentId = documentId;
        this.documentName = documentName;
        this.documentDescription = documentDescription;
    }
}
