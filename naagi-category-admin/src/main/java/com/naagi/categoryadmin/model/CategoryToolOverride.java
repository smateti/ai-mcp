package com.naagi.categoryadmin.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for storing category-specific tool-level overrides.
 * Allows customizing tool metadata (descriptions, usage guidance) per category.
 * This is separate from CategoryParameterOverride which handles parameter-level overrides.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "category_tool_overrides",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_category_tool_override",
           columnNames = {"category_id", "tool_id"}
       ),
       indexes = {
           @Index(name = "idx_cto_category_id", columnList = "category_id"),
           @Index(name = "idx_cto_tool_id", columnList = "tool_id")
       })
public class CategoryToolOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false, length = 100)
    private String categoryId;

    @Column(name = "tool_id", nullable = false, length = 255)
    private String toolId;

    /**
     * Override for tool's humanReadableDescription (null = use original).
     * Category-specific description of what this tool does.
     */
    @Column(name = "human_readable_description", length = 2000)
    private String humanReadableDescription;

    /**
     * Guidance on WHEN to use this tool in this category context.
     * Helps the AI model select the right tool.
     * Example: "Use this for batch job queries. For service apps, use getServiceInfo instead."
     */
    @Column(name = "when_to_use", length = 2000)
    private String whenToUse;

    /**
     * Guidance on when NOT to use this tool.
     * Helps prevent wrong tool selection.
     * Example: "Do not use for real-time status checks - use getAppStatus instead (faster)."
     */
    @Column(name = "when_not_to_use", length = 2000)
    private String whenNotToUse;

    /**
     * Category-specific usage examples.
     * Example: "To get batch job details: getAppInfo(appId='batch-job-123', appType='BATCH')"
     */
    @Column(name = "usage_examples", length = 4000)
    private String usageExamples;

    /**
     * Priority/preference score for this tool in this category.
     * Higher values = more preferred. Used when multiple tools could satisfy a request.
     */
    @Column(name = "priority_score")
    private Integer priorityScore;

    /**
     * Whether this override is active.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if this override has any effective override values.
     */
    public boolean hasAnyOverride() {
        return (humanReadableDescription != null && !humanReadableDescription.isBlank()) ||
               (whenToUse != null && !whenToUse.isBlank()) ||
               (whenNotToUse != null && !whenNotToUse.isBlank()) ||
               (usageExamples != null && !usageExamples.isBlank()) ||
               priorityScore != null;
    }
}
