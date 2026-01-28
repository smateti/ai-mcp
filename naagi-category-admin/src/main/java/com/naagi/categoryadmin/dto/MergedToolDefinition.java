package com.naagi.categoryadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for returning tool definitions with category-level overrides applied.
 * This is the primary response format for consumers (e.g., MCP Gateway).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergedToolDefinition {

    private Long id;
    private String toolId;
    private String name;
    private String description;
    private String humanReadableDescription;
    private String httpMethod;
    private String path;
    private String baseUrl;
    private String categoryId;

    // Tool-level override fields
    /**
     * Guidance on WHEN to use this tool in this category context.
     * Helps the AI model select the right tool.
     */
    private String whenToUse;

    /**
     * Guidance on when NOT to use this tool.
     * Helps prevent wrong tool selection.
     */
    private String whenNotToUse;

    /**
     * Category-specific usage examples.
     */
    private String usageExamples;

    /**
     * Priority score for tool selection (higher = more preferred).
     */
    private Integer priorityScore;

    /**
     * Whether tool-level description was overridden.
     */
    private boolean toolDescriptionOverridden;

    /**
     * Computed effective description combining base description with overrides and guidance.
     * This is the primary field for AI model consumption.
     */
    private String effectiveToolDescription;

    @Builder.Default
    private List<MergedParameter> parameters = new ArrayList<>();

    @Builder.Default
    private List<MergedResponse> responses = new ArrayList<>();

    /**
     * Merged parameter with override information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MergedParameter {
        private Long id;
        private String name;
        private String parameterPath;
        private String description;
        private String humanReadableDescription;
        private String effectiveDescription;
        private String type;
        private Boolean required;
        private String in;
        private String format;
        private String example;
        private Integer nestingLevel;
        private String enumValues;

        // Override indicators
        private boolean locked;
        private String lockedValue;
        private boolean descriptionOverridden;
        private boolean exampleOverridden;
        private boolean enumOverridden;

        @Builder.Default
        private List<MergedParameter> nestedParameters = new ArrayList<>();
    }

    /**
     * Merged response definition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MergedResponse {
        private Long id;
        private String statusCode;
        private String description;
        private String humanReadableDescription;
        private String type;
        private String schema;

        @Builder.Default
        private List<MergedParameter> parameters = new ArrayList<>();
    }
}
