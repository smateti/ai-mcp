package com.naag.categoryadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for presenting tools in a category with override statistics.
 * Used by the admin UI to show override summary information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryToolOverview {

    private String toolId;
    private String name;
    private String description;
    private String serviceUrl;
    private String method;
    private String path;
    private int totalParameters;
    private int overriddenParameters;
    private int lockedParameters;

    @Builder.Default
    private List<ParameterOverrideSummary> overrideSummaries = new ArrayList<>();

    /**
     * Summary of a parameter override.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterOverrideSummary {
        private Long overrideId;
        private String parameterPath;
        private String parameterName;
        private boolean hasDescriptionOverride;
        private boolean hasExampleOverride;
        private boolean hasEnumOverride;
        private boolean isLocked;
        private String lockedValue;
        private String enumValues;
    }
}
