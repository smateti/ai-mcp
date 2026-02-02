package com.naagi.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A relationship between two extracted entities.
 * Stores the relationship type and the source chunk where it was found.
 */
@Entity
@Table(name = "entity_relationships", indexes = {
    @Index(name = "idx_rel_source", columnList = "sourceEntityId"),
    @Index(name = "idx_rel_target", columnList = "targetEntityId"),
    @Index(name = "idx_rel_doc", columnList = "docId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String sourceEntityId;

    @Column(nullable = false)
    private String targetEntityId;

    @Column(nullable = false)
    private String relationshipType;

    private String description;

    @Column(nullable = false)
    private String docId;

    private String chunkId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
