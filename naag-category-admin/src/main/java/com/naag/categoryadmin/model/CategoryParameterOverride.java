package com.naag.categoryadmin.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for storing category-specific parameter overrides.
 * Allows the same tool to have different parameter configurations per category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "category_parameter_overrides",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_category_tool_param",
           columnNames = {"category_id", "tool_id", "parameter_path"}
       ),
       indexes = {
           @Index(name = "idx_cpo_category_tool", columnList = "category_id, tool_id"),
           @Index(name = "idx_cpo_category_id", columnList = "category_id"),
           @Index(name = "idx_cpo_tool_id", columnList = "tool_id")
       })
public class CategoryParameterOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false, length = 100)
    private String categoryId;

    @Column(name = "tool_id", nullable = false, length = 255)
    private String toolId;

    /**
     * Dot-notation path to identify the parameter, supporting nested parameters.
     * Examples: "appType", "request.body.type", "config.options.timeout"
     */
    @Column(name = "parameter_path", nullable = false, length = 500)
    private String parameterPath;

    /**
     * Override for humanReadableDescription (null = use original)
     */
    @Column(name = "human_readable_description", length = 2000)
    private String humanReadableDescription;

    /**
     * Override for example (null = use original)
     */
    @Column(name = "example", length = 1000)
    private String example;

    /**
     * Override for enumValues - comma-separated list (null = use original).
     * This restricts the allowed values to a subset of the original enum.
     */
    @Column(name = "enum_values", length = 2000)
    private String enumValues;

    /**
     * Locked value - if set, parameter is pre-filled and cannot be changed.
     * Takes precedence over enumValues override.
     */
    @Column(name = "locked_value", length = 1000)
    private String lockedValue;

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
     * Check if this override has a locked value set.
     */
    public boolean isLocked() {
        return lockedValue != null && !lockedValue.isBlank();
    }

    /**
     * Check if this override has any effective override values.
     */
    public boolean hasAnyOverride() {
        return (humanReadableDescription != null && !humanReadableDescription.isBlank()) ||
               (example != null && !example.isBlank()) ||
               (enumValues != null && !enumValues.isBlank()) ||
               isLocked();
    }
}
