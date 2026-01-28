package com.naagi.categoryadmin.controller;

import com.naagi.categoryadmin.dto.CategoryToolOverview;
import com.naagi.categoryadmin.dto.MergedToolDefinition;
import com.naagi.categoryadmin.model.CategoryParameterOverride;
import com.naagi.categoryadmin.service.CategoryParameterOverrideService;
import com.naagi.categoryadmin.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing category-level parameter overrides.
 */
@RestController
@RequestMapping("/api/categories/{categoryId}/tools")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CategoryParameterOverrideController {

    private final CategoryParameterOverrideService overrideService;
    private final CategoryService categoryService;

    // ==================== Override CRUD ====================

    /**
     * Get all parameter overrides for a specific tool in a category.
     */
    @GetMapping("/{toolId}/overrides")
    public ResponseEntity<List<CategoryParameterOverride>> getOverrides(
            @PathVariable String categoryId,
            @PathVariable String toolId) {
        return ResponseEntity.ok(overrideService.getOverridesForTool(categoryId, toolId));
    }

    /**
     * Create or update a parameter override.
     */
    @PostMapping("/{toolId}/overrides")
    public ResponseEntity<CategoryParameterOverride> createOrUpdateOverride(
            @PathVariable String categoryId,
            @PathVariable String toolId,
            @RequestBody ParameterOverrideRequest request) {

        CategoryParameterOverride override = CategoryParameterOverride.builder()
                .categoryId(categoryId)
                .toolId(toolId)
                .parameterPath(request.parameterPath())
                .humanReadableDescription(request.humanReadableDescription())
                .example(request.example())
                .enumValues(request.enumValues())
                .lockedValue(request.lockedValue())
                .active(true)
                .build();

        return ResponseEntity.ok(overrideService.createOrUpdateOverride(override));
    }

    /**
     * Bulk update overrides for a tool.
     */
    @PutMapping("/{toolId}/overrides/bulk")
    public ResponseEntity<Map<String, Object>> bulkUpdateOverrides(
            @PathVariable String categoryId,
            @PathVariable String toolId,
            @RequestBody List<ParameterOverrideRequest> requests) {

        int updated = 0;
        for (ParameterOverrideRequest request : requests) {
            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .categoryId(categoryId)
                    .toolId(toolId)
                    .parameterPath(request.parameterPath())
                    .humanReadableDescription(request.humanReadableDescription())
                    .example(request.example())
                    .enumValues(request.enumValues())
                    .lockedValue(request.lockedValue())
                    .active(true)
                    .build();
            overrideService.createOrUpdateOverride(override);
            updated++;
        }

        return ResponseEntity.ok(Map.of("success", true, "updated", updated));
    }

    /**
     * Delete a specific override.
     */
    @DeleteMapping("/{toolId}/overrides/{overrideId}")
    public ResponseEntity<Void> deleteOverride(
            @PathVariable String categoryId,
            @PathVariable String toolId,
            @PathVariable Long overrideId) {
        overrideService.deleteOverride(overrideId);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete all overrides for a tool in this category.
     */
    @DeleteMapping("/{toolId}/overrides")
    public ResponseEntity<Void> deleteAllOverridesForTool(
            @PathVariable String categoryId,
            @PathVariable String toolId) {
        overrideService.deleteOverridesForTool(categoryId, toolId);
        return ResponseEntity.ok().build();
    }

    // ==================== Merged Tool Retrieval ====================

    /**
     * Get a single tool definition with overrides applied.
     * This is the primary endpoint for consumers (e.g., MCP Gateway).
     */
    @GetMapping("/{toolId}/merged")
    public ResponseEntity<MergedToolDefinition> getMergedTool(
            @PathVariable String categoryId,
            @PathVariable String toolId) {
        return overrideService.getMergedToolDefinition(categoryId, toolId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all tools for a category with overrides applied.
     */
    @GetMapping("/merged")
    public ResponseEntity<List<MergedToolDefinition>> getAllMergedTools(
            @PathVariable String categoryId) {
        return categoryService.getCategory(categoryId)
                .map(category -> {
                    List<String> toolIds = category.getToolIds();
                    if (toolIds == null || toolIds.isEmpty()) {
                        return ResponseEntity.ok(List.<MergedToolDefinition>of());
                    }
                    return ResponseEntity.ok(overrideService.getMergedToolsForCategory(categoryId, toolIds));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get tool overview with override statistics (for admin UI).
     */
    @GetMapping("/overview")
    public ResponseEntity<List<CategoryToolOverview>> getToolOverviews(
            @PathVariable String categoryId) {
        return categoryService.getCategory(categoryId)
                .map(category -> {
                    List<String> toolIds = category.getToolIds();
                    if (toolIds == null || toolIds.isEmpty()) {
                        return ResponseEntity.ok(List.<CategoryToolOverview>of());
                    }
                    return ResponseEntity.ok(overrideService.getToolOverviewsForCategory(categoryId, toolIds));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Request DTOs ====================

    /**
     * Request DTO for creating/updating a parameter override.
     */
    public record ParameterOverrideRequest(
            String parameterPath,
            String humanReadableDescription,
            String example,
            String enumValues,
            String lockedValue
    ) {}
}
