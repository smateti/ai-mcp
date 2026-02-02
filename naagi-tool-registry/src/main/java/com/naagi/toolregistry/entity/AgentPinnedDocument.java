package com.naagi.toolregistry.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_pinned_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentPinnedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    @JsonIgnore
    private AgentDefinition agent;

    @Column(nullable = false)
    private String docId; // Reference to RAG document

    @Column(length = 500)
    private String docTitle;

    @Builder.Default
    private int maxTokens = 2000;

    @Builder.Default
    private int priority = 0; // Lower = first
}
