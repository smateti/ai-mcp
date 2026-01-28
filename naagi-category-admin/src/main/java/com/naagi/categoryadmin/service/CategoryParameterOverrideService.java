package com.naagi.categoryadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.naagi.categoryadmin.client.ToolRegistryClient;
import com.naagi.categoryadmin.dto.CategoryToolOverview;
import com.naagi.categoryadmin.dto.MergedToolDefinition;
import com.naagi.categoryadmin.model.CategoryParameterOverride;
import com.naagi.categoryadmin.model.CategoryToolOverride;
import com.naagi.categoryadmin.repository.CategoryParameterOverrideRepository;
import com.naagi.categoryadmin.repository.CategoryToolOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing category-level parameter and tool overrides.
 * Handles CRUD operations and merging of tool definitions with overrides.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryParameterOverrideService {

    private final CategoryParameterOverrideRepository overrideRepository;
    private final CategoryToolOverrideRepository toolOverrideRepository;
    private final ToolRegistryClient toolRegistryClient;

    // ==================== Override CRUD Operations ====================

    /**
     * Create or update a parameter override.
     */
    @Transactional
    public CategoryParameterOverride createOrUpdateOverride(CategoryParameterOverride override) {
        Optional<CategoryParameterOverride> existing = overrideRepository
                .findByCategoryIdAndToolIdAndParameterPath(
                        override.getCategoryId(),
                        override.getToolId(),
                        override.getParameterPath());

        if (existing.isPresent()) {
            CategoryParameterOverride existingOverride = existing.get();
            existingOverride.setHumanReadableDescription(override.getHumanReadableDescription());
            existingOverride.setExample(override.getExample());
            existingOverride.setEnumValues(override.getEnumValues());
            existingOverride.setLockedValue(override.getLockedValue());
            existingOverride.setActive(override.isActive());
            log.info("Updated override for category={}, tool={}, param={}",
                    override.getCategoryId(), override.getToolId(), override.getParameterPath());
            return overrideRepository.save(existingOverride);
        }

        log.info("Created override for category={}, tool={}, param={}",
                override.getCategoryId(), override.getToolId(), override.getParameterPath());
        return overrideRepository.save(override);
    }

    /**
     * Get an override by ID.
     */
    public Optional<CategoryParameterOverride> getOverride(Long id) {
        return overrideRepository.findById(id);
    }

    /**
     * Delete an override by ID.
     */
    @Transactional
    public void deleteOverride(Long id) {
        overrideRepository.deleteById(id);
        log.info("Deleted override id={}", id);
    }

    /**
     * Delete all overrides for a tool in a category.
     */
    @Transactional
    public void deleteOverridesForTool(String categoryId, String toolId) {
        overrideRepository.deleteByCategoryIdAndToolId(categoryId, toolId);
        log.info("Deleted all overrides for category={}, tool={}", categoryId, toolId);
    }

    /**
     * Delete all overrides for a category.
     */
    @Transactional
    public void deleteOverridesForCategory(String categoryId) {
        overrideRepository.deleteByCategoryId(categoryId);
        log.info("Deleted all overrides for category={}", categoryId);
    }

    /**
     * Get all overrides for a tool in a category.
     */
    public List<CategoryParameterOverride> getOverridesForTool(String categoryId, String toolId) {
        return overrideRepository.findByCategoryIdAndToolIdAndActiveTrue(categoryId, toolId);
    }

    /**
     * Get all overrides for a category.
     */
    public List<CategoryParameterOverride> getOverridesForCategory(String categoryId) {
        return overrideRepository.findByCategoryIdAndActiveTrue(categoryId);
    }

    // ==================== Tool-Level Override CRUD ====================

    /**
     * Create or update a tool-level override.
     */
    @Transactional
    public CategoryToolOverride createOrUpdateToolOverride(CategoryToolOverride override) {
        Optional<CategoryToolOverride> existing = toolOverrideRepository
                .findByCategoryIdAndToolId(override.getCategoryId(), override.getToolId());

        if (existing.isPresent()) {
            CategoryToolOverride existingOverride = existing.get();
            existingOverride.setHumanReadableDescription(override.getHumanReadableDescription());
            existingOverride.setWhenToUse(override.getWhenToUse());
            existingOverride.setWhenNotToUse(override.getWhenNotToUse());
            existingOverride.setUsageExamples(override.getUsageExamples());
            existingOverride.setPriorityScore(override.getPriorityScore());
            existingOverride.setActive(override.isActive());
            log.info("Updated tool override for category={}, tool={}", override.getCategoryId(), override.getToolId());
            return toolOverrideRepository.save(existingOverride);
        }

        log.info("Created tool override for category={}, tool={}", override.getCategoryId(), override.getToolId());
        return toolOverrideRepository.save(override);
    }

    /**
     * Get tool-level override for a specific category and tool.
     */
    public Optional<CategoryToolOverride> getToolOverride(String categoryId, String toolId) {
        return toolOverrideRepository.findByCategoryIdAndToolIdAndActiveTrue(categoryId, toolId);
    }

    /**
     * Delete tool-level override.
     */
    @Transactional
    public void deleteToolOverride(String categoryId, String toolId) {
        toolOverrideRepository.deleteByCategoryIdAndToolId(categoryId, toolId);
        log.info("Deleted tool override for category={}, tool={}", categoryId, toolId);
    }

    // ==================== Merge Logic ====================

    /**
     * Get a tool definition with category overrides applied.
     * This is the main method for fetching context-aware tool definitions.
     */
    public Optional<MergedToolDefinition> getMergedToolDefinition(String categoryId, String toolId) {
        Optional<JsonNode> toolDetails = toolRegistryClient.getToolDetails(toolId);
        if (toolDetails.isEmpty()) {
            log.warn("Tool not found in registry: {}", toolId);
            return Optional.empty();
        }

        JsonNode baseTool = toolDetails.get();

        // Get parameter-level overrides
        List<CategoryParameterOverride> overrides = overrideRepository
                .findByCategoryIdAndToolIdAndActiveTrue(categoryId, toolId);

        Map<String, CategoryParameterOverride> overrideMap = overrides.stream()
                .collect(Collectors.toMap(
                        CategoryParameterOverride::getParameterPath,
                        o -> o,
                        (a, b) -> a
                ));

        // Get tool-level override
        Optional<CategoryToolOverride> toolOverride = toolOverrideRepository
                .findByCategoryIdAndToolIdAndActiveTrue(categoryId, toolId);

        return Optional.of(mergeTool(baseTool, overrideMap, toolOverride.orElse(null), categoryId));
    }

    /**
     * Get all tools for a category with overrides applied.
     */
    public List<MergedToolDefinition> getMergedToolsForCategory(String categoryId, List<String> toolIds) {
        // Get all parameter-level overrides
        List<CategoryParameterOverride> allOverrides = overrideRepository
                .findByCategoryIdAndActiveTrue(categoryId);

        Map<String, List<CategoryParameterOverride>> overridesByTool = allOverrides.stream()
                .collect(Collectors.groupingBy(CategoryParameterOverride::getToolId));

        // Get all tool-level overrides
        List<CategoryToolOverride> allToolOverrides = toolOverrideRepository
                .findByCategoryIdAndActiveTrue(categoryId);

        Map<String, CategoryToolOverride> toolOverridesByTool = allToolOverrides.stream()
                .collect(Collectors.toMap(CategoryToolOverride::getToolId, o -> o, (a, b) -> a));

        List<MergedToolDefinition> mergedTools = new ArrayList<>();

        for (String toolId : toolIds) {
            Optional<JsonNode> toolDetails = toolRegistryClient.getToolDetails(toolId);
            if (toolDetails.isEmpty()) {
                log.warn("Tool not found in registry: {}", toolId);
                continue;
            }

            List<CategoryParameterOverride> paramOverrides = overridesByTool.getOrDefault(toolId, List.of());
            Map<String, CategoryParameterOverride> overrideMap = paramOverrides.stream()
                    .collect(Collectors.toMap(
                            CategoryParameterOverride::getParameterPath,
                            o -> o,
                            (a, b) -> a
                    ));

            CategoryToolOverride toolOverride = toolOverridesByTool.get(toolId);

            mergedTools.add(mergeTool(toolDetails.get(), overrideMap, toolOverride, categoryId));
        }

        return mergedTools;
    }

    /**
     * Merge base tool definition with parameter and tool-level overrides.
     */
    private MergedToolDefinition mergeTool(JsonNode baseTool, Map<String, CategoryParameterOverride> overrideMap,
                                           CategoryToolOverride toolOverride, String categoryId) {
        String baseHumanDesc = getTextOrNull(baseTool, "humanReadableDescription");

        MergedToolDefinition merged = MergedToolDefinition.builder()
                .id(baseTool.has("id") ? baseTool.get("id").asLong() : null)
                .toolId(baseTool.get("toolId").asText())
                .name(baseTool.get("name").asText())
                .description(getTextOrNull(baseTool, "description"))
                .humanReadableDescription(baseHumanDesc)
                .httpMethod(baseTool.get("httpMethod").asText())
                .path(baseTool.get("path").asText())
                .baseUrl(getTextOrNull(baseTool, "baseUrl"))
                .categoryId(categoryId)
                .build();

        // Apply tool-level overrides
        if (toolOverride != null) {
            // Description override
            if (toolOverride.getHumanReadableDescription() != null && !toolOverride.getHumanReadableDescription().isBlank()) {
                merged.setHumanReadableDescription(toolOverride.getHumanReadableDescription());
                merged.setToolDescriptionOverridden(true);
            }

            // Tool selection guidance
            merged.setWhenToUse(toolOverride.getWhenToUse());
            merged.setWhenNotToUse(toolOverride.getWhenNotToUse());
            merged.setUsageExamples(toolOverride.getUsageExamples());
            merged.setPriorityScore(toolOverride.getPriorityScore());
        }

        // Compute effective tool description for AI consumption
        merged.setEffectiveToolDescription(computeEffectiveToolDescription(merged));

        // Merge parameters
        if (baseTool.has("parameters") && baseTool.get("parameters").isArray()) {
            List<MergedToolDefinition.MergedParameter> mergedParams = new ArrayList<>();
            for (JsonNode param : baseTool.get("parameters")) {
                if (param.has("parentParameter") && !param.get("parentParameter").isNull()) {
                    continue; // Skip nested params at root level, they'll be processed recursively
                }
                mergedParams.add(mergeParameter(param, "", overrideMap));
            }
            merged.setParameters(mergedParams);
        }

        // Merge responses
        if (baseTool.has("responses") && baseTool.get("responses").isArray()) {
            List<MergedToolDefinition.MergedResponse> mergedResponses = new ArrayList<>();
            for (JsonNode response : baseTool.get("responses")) {
                mergedResponses.add(mergeResponse(response, overrideMap));
            }
            merged.setResponses(mergedResponses);
        }

        return merged;
    }

    /**
     * Compute the effective tool description for AI model consumption.
     * Combines base description with whenToUse/whenNotToUse guidance.
     */
    private String computeEffectiveToolDescription(MergedToolDefinition tool) {
        StringBuilder sb = new StringBuilder();

        // Start with human-readable description or fall back to description
        String baseDesc = tool.getHumanReadableDescription() != null && !tool.getHumanReadableDescription().isBlank()
                ? tool.getHumanReadableDescription()
                : tool.getDescription();

        if (baseDesc != null && !baseDesc.isBlank()) {
            sb.append(baseDesc);
        }

        // Add "when to use" guidance
        if (tool.getWhenToUse() != null && !tool.getWhenToUse().isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("USE WHEN: ").append(tool.getWhenToUse());
        }

        // Add "when NOT to use" guidance
        if (tool.getWhenNotToUse() != null && !tool.getWhenNotToUse().isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("DO NOT USE WHEN: ").append(tool.getWhenNotToUse());
        }

        return sb.toString();
    }

    /**
     * Merge a single parameter with its override (if any), handling nested parameters recursively.
     */
    private MergedToolDefinition.MergedParameter mergeParameter(
            JsonNode baseParam,
            String parentPath,
            Map<String, CategoryParameterOverride> overrideMap) {

        String paramName = baseParam.get("name").asText();
        String paramPath = parentPath.isEmpty() ? paramName : parentPath + "." + paramName;

        CategoryParameterOverride override = overrideMap.get(paramPath);

        MergedToolDefinition.MergedParameter merged = MergedToolDefinition.MergedParameter.builder()
                .id(baseParam.has("id") ? baseParam.get("id").asLong() : null)
                .name(paramName)
                .parameterPath(paramPath)
                .description(getTextOrNull(baseParam, "description"))
                .type(baseParam.has("type") ? baseParam.get("type").asText() : "string")
                .required(baseParam.has("required") && baseParam.get("required").asBoolean())
                .in(getTextOrNull(baseParam, "in"))
                .format(getTextOrNull(baseParam, "format"))
                .nestingLevel(baseParam.has("nestingLevel") ? baseParam.get("nestingLevel").asInt() : 0)
                .build();

        // Base values
        String baseHumanDesc = getTextOrNull(baseParam, "humanReadableDescription");
        String baseExample = getTextOrNull(baseParam, "example");
        String baseEnumValues = getTextOrNull(baseParam, "enumValues");

        // Apply overrides
        if (override != null) {
            // Description override
            if (override.getHumanReadableDescription() != null && !override.getHumanReadableDescription().isBlank()) {
                merged.setHumanReadableDescription(override.getHumanReadableDescription());
                merged.setDescriptionOverridden(true);
            } else {
                merged.setHumanReadableDescription(baseHumanDesc);
            }

            // Example override
            if (override.getExample() != null && !override.getExample().isBlank()) {
                merged.setExample(override.getExample());
                merged.setExampleOverridden(true);
            } else {
                merged.setExample(baseExample);
            }

            // Locked value takes highest precedence
            if (override.getLockedValue() != null && !override.getLockedValue().isBlank()) {
                merged.setLocked(true);
                merged.setLockedValue(override.getLockedValue());
                merged.setEnumValues(override.getLockedValue());
            }
            // Enum override
            else if (override.getEnumValues() != null && !override.getEnumValues().isBlank()) {
                merged.setEnumValues(override.getEnumValues());
                merged.setEnumOverridden(true);
            } else {
                merged.setEnumValues(baseEnumValues);
            }
        } else {
            merged.setHumanReadableDescription(baseHumanDesc);
            merged.setExample(baseExample);
            merged.setEnumValues(baseEnumValues);
        }

        // Compute effective description
        merged.setEffectiveDescription(computeEffectiveDescription(merged));

        // Process nested parameters
        if (baseParam.has("nestedParameters") && baseParam.get("nestedParameters").isArray()) {
            List<MergedToolDefinition.MergedParameter> nestedMerged = new ArrayList<>();
            for (JsonNode nestedParam : baseParam.get("nestedParameters")) {
                nestedMerged.add(mergeParameter(nestedParam, paramPath, overrideMap));
            }
            merged.setNestedParameters(nestedMerged);
        }

        return merged;
    }

    /**
     * Merge response definition.
     */
    private MergedToolDefinition.MergedResponse mergeResponse(
            JsonNode baseResponse,
            Map<String, CategoryParameterOverride> overrideMap) {

        MergedToolDefinition.MergedResponse merged = MergedToolDefinition.MergedResponse.builder()
                .id(baseResponse.has("id") ? baseResponse.get("id").asLong() : null)
                .statusCode(getTextOrNull(baseResponse, "statusCode"))
                .description(getTextOrNull(baseResponse, "description"))
                .humanReadableDescription(getTextOrNull(baseResponse, "humanReadableDescription"))
                .type(getTextOrNull(baseResponse, "type"))
                .schema(getTextOrNull(baseResponse, "schema"))
                .build();

        // Process response parameters with "response." prefix
        if (baseResponse.has("parameters") && baseResponse.get("parameters").isArray()) {
            List<MergedToolDefinition.MergedParameter> responseParams = new ArrayList<>();
            for (JsonNode param : baseResponse.get("parameters")) {
                if (param.has("parentParameter") && !param.get("parentParameter").isNull()) {
                    continue;
                }
                responseParams.add(mergeParameter(param, "response", overrideMap));
            }
            merged.setParameters(responseParams);
        }

        return merged;
    }

    /**
     * Compute effective description (description + locked/enum info).
     */
    private String computeEffectiveDescription(MergedToolDefinition.MergedParameter param) {
        String baseDesc = param.getHumanReadableDescription() != null && !param.getHumanReadableDescription().isBlank()
                ? param.getHumanReadableDescription()
                : param.getDescription();

        if (baseDesc == null) {
            baseDesc = "";
        }

        if (param.isLocked() && param.getLockedValue() != null) {
            if (!baseDesc.isBlank()) {
                return baseDesc + ". [LOCKED to: " + param.getLockedValue() + "]";
            } else {
                return "[LOCKED to: " + param.getLockedValue() + "]";
            }
        }

        if (param.getEnumValues() != null && !param.getEnumValues().isBlank()) {
            String formattedEnums = param.getEnumValues().replace(",", ", ");
            if (!baseDesc.isBlank()) {
                return baseDesc + ". Allowed values: " + formattedEnums;
            } else {
                return "Allowed values: " + formattedEnums;
            }
        }

        return baseDesc;
    }

    // ==================== Overview/Summary Methods ====================

    /**
     * Get overview of tools in a category with override statistics.
     */
    public List<CategoryToolOverview> getToolOverviewsForCategory(String categoryId, List<String> toolIds) {
        List<CategoryToolOverview> overviews = new ArrayList<>();

        for (String toolId : toolIds) {
            Optional<JsonNode> toolDetails = toolRegistryClient.getToolDetails(toolId);
            if (toolDetails.isEmpty()) continue;

            JsonNode tool = toolDetails.get();
            List<CategoryParameterOverride> overrides = overrideRepository
                    .findByCategoryIdAndToolIdAndActiveTrue(categoryId, toolId);

            int totalParams = countParameters(tool);
            int overriddenParams = (int) overrides.stream().filter(o -> !o.isLocked()).count();
            int lockedParams = (int) overrides.stream().filter(CategoryParameterOverride::isLocked).count();

            List<CategoryToolOverview.ParameterOverrideSummary> summaries = overrides.stream()
                    .map(o -> CategoryToolOverview.ParameterOverrideSummary.builder()
                            .overrideId(o.getId())
                            .parameterPath(o.getParameterPath())
                            .parameterName(o.getParameterPath().contains(".") ?
                                    o.getParameterPath().substring(o.getParameterPath().lastIndexOf('.') + 1) :
                                    o.getParameterPath())
                            .hasDescriptionOverride(o.getHumanReadableDescription() != null && !o.getHumanReadableDescription().isBlank())
                            .hasExampleOverride(o.getExample() != null && !o.getExample().isBlank())
                            .hasEnumOverride(o.getEnumValues() != null && !o.getEnumValues().isBlank())
                            .isLocked(o.isLocked())
                            .lockedValue(o.getLockedValue())
                            .enumValues(o.getEnumValues())
                            .build())
                    .toList();

            overviews.add(CategoryToolOverview.builder()
                    .toolId(tool.get("toolId").asText())
                    .name(tool.get("name").asText())
                    .description(getTextOrNull(tool, "description"))
                    .serviceUrl(getTextOrNull(tool, "baseUrl"))
                    .method(tool.get("httpMethod").asText())
                    .path(tool.get("path").asText())
                    .totalParameters(totalParams)
                    .overriddenParameters(overriddenParams)
                    .lockedParameters(lockedParams)
                    .overrideSummaries(summaries)
                    .build());
        }

        return overviews;
    }

    /**
     * Count total parameters in a tool (including nested).
     */
    private int countParameters(JsonNode tool) {
        int count = 0;
        if (tool.has("parameters") && tool.get("parameters").isArray()) {
            count += countParametersRecursive(tool.get("parameters"));
        }
        return count;
    }

    private int countParametersRecursive(JsonNode params) {
        int count = 0;
        for (JsonNode param : params) {
            // Only count root-level params (those without parentParameter)
            if (!param.has("parentParameter") || param.get("parentParameter").isNull()) {
                count++;
            }
            if (param.has("nestedParameters") && param.get("nestedParameters").isArray()) {
                count += countParametersRecursive(param.get("nestedParameters"));
            }
        }
        return count;
    }

    /**
     * Helper to get text value or null from JsonNode.
     */
    private String getTextOrNull(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }
}
