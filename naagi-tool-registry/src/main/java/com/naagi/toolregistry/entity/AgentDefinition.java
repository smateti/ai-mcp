package com.naagi.toolregistry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String agentId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String role;

    @Column(nullable = false, length = 50)
    private String agentType; // INTERNAL, EXTERNAL, COMPOSITE

    @Column(length = 100)
    private String categoryId;

    @Column(length = 500)
    private String endpoint; // For EXTERNAL: A2A endpoint URL

    @Column(length = 500)
    private String healthCheckUrl;

    @Column(length = 50)
    @Builder.Default
    private String documentAccess = "AUTO"; // AUTO, NONE, CUSTOM

    @Builder.Default
    private int maxSteps = 10;

    @Builder.Default
    private boolean parallelToolCalls = true;

    @Builder.Default
    private boolean planningEnabled = true;

    @Builder.Default
    private boolean reflectionEnabled = true;

    @Column(columnDefinition = "TEXT")
    private String systemPromptOverride;

    @Column(length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, MAINTENANCE

    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentSkill> skills = new ArrayList<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentTool> tools = new ArrayList<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentPinnedDocument> pinnedDocuments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
