package com.example.categoryadmin.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Links MCP tools to categories for scoped tool availability
 */
@Entity
@Table(name = "category_tools",
       uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "tool_id"}))
@Data
@NoArgsConstructor
public class CategoryTool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @NotBlank(message = "Tool ID is required")
    @Column(name = "tool_id", nullable = false, length = 100)
    private String toolId;

    @Column(name = "tool_name", length = 200)
    private String toolName;

    @Column(name = "tool_description", length = 500)
    private String toolDescription;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "priority")
    private Integer priority = 0;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }

    public CategoryTool(Category category, String toolId, String toolName, String toolDescription) {
        this.category = category;
        this.toolId = toolId;
        this.toolName = toolName;
        this.toolDescription = toolDescription;
    }
}
