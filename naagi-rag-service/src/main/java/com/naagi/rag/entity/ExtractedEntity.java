package com.naagi.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An entity (person, system, concept, API, etc.) extracted from a document chunk
 * during ingestion. Used for graph-augmented retrieval.
 */
@Entity
@Table(name = "extracted_entities", indexes = {
    @Index(name = "idx_entity_name", columnList = "name"),
    @Index(name = "idx_entity_type", columnList = "entityType"),
    @Index(name = "idx_entity_doc", columnList = "docId"),
    @Index(name = "idx_entity_category", columnList = "categoryId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String entityType;

    private String description;

    @Column(nullable = false)
    private String docId;

    private String categoryId;

    private int mentionCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (mentionCount == 0) mentionCount = 1;
    }
}
