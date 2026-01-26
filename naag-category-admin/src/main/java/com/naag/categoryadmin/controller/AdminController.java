package com.naag.categoryadmin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.naag.categoryadmin.client.ChatAppClient;
import com.naag.categoryadmin.client.RagServiceClient;
import com.naag.categoryadmin.client.ToolRegistryClient;
import com.naag.categoryadmin.model.AuditLog;
import com.naag.categoryadmin.model.Category;
import com.naag.categoryadmin.model.CategoryParameterOverride;
import com.naag.categoryadmin.model.Tool;
import com.naag.categoryadmin.service.AuditLogService;
import com.naag.categoryadmin.service.CategoryParameterOverrideService;
import com.naag.categoryadmin.service.CategoryService;
import com.naag.categoryadmin.service.DocumentParserService;
import com.naag.categoryadmin.service.SetupDataInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final CategoryService categoryService;
    private final CategoryParameterOverrideService overrideService;
    private final ToolRegistryClient toolRegistryClient;
    private final RagServiceClient ragServiceClient;
    private final DocumentParserService documentParserService;
    private final SetupDataInitializer setupDataInitializer;
    private final AuditLogService auditLogService;
    private final ChatAppClient chatAppClient;

    @GetMapping("/")
    public String dashboard(Model model) {
        List<Category> categories = categoryService.getAllCategories();
        List<Tool> tools = toolRegistryClient.getAllTools();

        model.addAttribute("activePage", "dashboard");
        model.addAttribute("categories", categories);
        model.addAttribute("tools", tools);
        model.addAttribute("categoryCount", categories.size());
        model.addAttribute("toolCount", tools.size());
        model.addAttribute("activeCategories", categoryService.getActiveCategories().size());

        // Add RAG document stats
        try {
            var ragStats = ragServiceClient.getStats();
            model.addAttribute("ragStats", ragStats);
            model.addAttribute("documentCount", ragStats.has("totalDocuments") ? ragStats.get("totalDocuments").asInt() : 0);
        } catch (Exception e) {
            log.warn("Could not fetch RAG stats for dashboard", e);
            model.addAttribute("documentCount", 0);
        }

        // Add upload count for dashboard
        try {
            model.addAttribute("uploadCount", ragServiceClient.getTotalUploadCount());
        } catch (Exception e) {
            log.warn("Could not fetch upload count for dashboard", e);
            model.addAttribute("uploadCount", 0);
        }

        return "dashboard";
    }

    // Category Management
    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("activePage", "categories");
        model.addAttribute("categories", categoryService.getAllCategories());

        // Add document counts per category
        try {
            model.addAttribute("documentCounts", ragServiceClient.getUploadCountsByCategory());
        } catch (Exception e) {
            log.warn("Could not fetch document counts", e);
            model.addAttribute("documentCounts", java.util.Collections.emptyMap());
        }

        return "categories/list";
    }

    @GetMapping("/categories/{id}")
    public String viewCategory(@PathVariable String id, Model model) {
        model.addAttribute("activePage", "categories");

        categoryService.getCategory(id).ifPresentOrElse(
                category -> {
                    model.addAttribute("category", category);

                    // Get tools for this category
                    if (category.getToolIds() != null && !category.getToolIds().isEmpty()) {
                        List<Tool> categoryTools = new java.util.ArrayList<>();
                        for (String toolId : category.getToolIds()) {
                            Optional<Tool> toolOpt = toolRegistryClient.getTool(toolId);
                            if (toolOpt.isPresent()) {
                                categoryTools.add(toolOpt.get());
                            } else {
                                log.warn("Tool {} assigned to category {} not found in registry", toolId, id);
                            }
                        }
                        model.addAttribute("categoryTools", categoryTools);
                    }

                    // Get documents for this category
                    try {
                        model.addAttribute("documents", ragServiceClient.getDocumentsByCategory(id));
                    } catch (Exception e) {
                        log.warn("Could not fetch documents for category {}", id, e);
                    }

                    // Get uploads for this category
                    try {
                        var allUploads = ragServiceClient.getDocumentUploads(null);
                        if (allUploads != null && allUploads.isArray()) {
                            var filteredUploads = new java.util.ArrayList<>();
                            for (var upload : allUploads) {
                                if (upload.has("categoryId") && id.equals(upload.get("categoryId").asText())) {
                                    filteredUploads.add(upload);
                                }
                            }
                            model.addAttribute("uploads", filteredUploads);
                        }
                    } catch (Exception e) {
                        log.warn("Could not fetch uploads for category {}", id, e);
                    }

                    // Get all tools for assignment
                    model.addAttribute("allTools", toolRegistryClient.getAllTools());
                },
                () -> model.addAttribute("error", "Category not found")
        );

        return "categories/view";
    }

    @PostMapping("/categories/{id}/tools/add")
    public String addToolToCategory(@PathVariable String id,
                                     @RequestParam String toolId,
                                     RedirectAttributes redirectAttributes) {
        try {
            categoryService.addToolToCategory(id, toolId);
            redirectAttributes.addFlashAttribute("success", "Tool added to category");
            auditLogService.logToolAddToCategory("admin", toolId, id);
        } catch (Exception e) {
            log.error("Error adding tool to category", e);
            redirectAttributes.addFlashAttribute("error", "Failed to add tool: " + e.getMessage());
        }
        return "redirect:/categories/" + id;
    }

    @PostMapping("/categories/{id}/tools/{toolId}/remove")
    public String removeToolFromCategory(@PathVariable String id,
                                          @PathVariable String toolId,
                                          RedirectAttributes redirectAttributes) {
        try {
            categoryService.removeToolFromCategory(id, toolId);
            redirectAttributes.addFlashAttribute("success", "Tool removed from category");
            auditLogService.logToolRemoveFromCategory("admin", toolId, id);
        } catch (Exception e) {
            log.error("Error removing tool from category", e);
            redirectAttributes.addFlashAttribute("error", "Failed to remove tool: " + e.getMessage());
        }
        return "redirect:/categories/" + id;
    }

    @PostMapping("/categories/{id}/tools/reset-priorities")
    public String resetToolPriorities(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.resetToolPriorities(id);
            redirectAttributes.addFlashAttribute("success", "Tool priorities reset");
        } catch (Exception e) {
            log.error("Error resetting tool priorities", e);
            redirectAttributes.addFlashAttribute("error", "Failed to reset priorities: " + e.getMessage());
        }
        return "redirect:/categories/" + id;
    }

    // Tool Parameter Overrides
    @GetMapping("/categories/{categoryId}/tools/{toolId}/overrides")
    public String toolParameterOverrides(@PathVariable String categoryId,
                                          @PathVariable String toolId,
                                          Model model) {
        model.addAttribute("activePage", "categories");

        categoryService.getCategory(categoryId).ifPresentOrElse(
                category -> {
                    model.addAttribute("category", category);

                    // Get tool details
                    toolRegistryClient.getToolDetails(toolId).ifPresentOrElse(
                            toolDetails -> {
                                model.addAttribute("tool", toolDetails);

                                // Get existing parameter overrides for this tool
                                List<CategoryParameterOverride> overrides =
                                        overrideService.getOverridesForTool(categoryId, toolId);
                                model.addAttribute("overrides", overrides);

                                // Create a map for easy lookup in template
                                java.util.Map<String, CategoryParameterOverride> overrideMap = new java.util.HashMap<>();
                                for (CategoryParameterOverride o : overrides) {
                                    overrideMap.put(o.getParameterPath(), o);
                                }
                                model.addAttribute("overrideMap", overrideMap);

                                // Get tool-level override
                                overrideService.getToolOverride(categoryId, toolId)
                                        .ifPresent(toolOverride -> model.addAttribute("toolOverride", toolOverride));
                            },
                            () -> model.addAttribute("error", "Tool not found")
                    );
                },
                () -> model.addAttribute("error", "Category not found")
        );

        return "categories/tool-overrides";
    }

    @PostMapping("/categories/{categoryId}/tools/{toolId}/overrides")
    public String saveToolParameterOverride(@PathVariable String categoryId,
                                             @PathVariable String toolId,
                                             @RequestParam String parameterPath,
                                             @RequestParam(required = false) String humanReadableDescription,
                                             @RequestParam(required = false) String example,
                                             @RequestParam(required = false) String enumValues,
                                             @RequestParam(required = false) String lockedValue,
                                             RedirectAttributes redirectAttributes) {
        try {
            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .categoryId(categoryId)
                    .toolId(toolId)
                    .parameterPath(parameterPath)
                    .humanReadableDescription(humanReadableDescription)
                    .example(example)
                    .enumValues(enumValues)
                    .lockedValue(lockedValue)
                    .active(true)
                    .build();

            overrideService.createOrUpdateOverride(override);
            redirectAttributes.addFlashAttribute("success", "Override saved for parameter: " + parameterPath);
            auditLogService.logParameterOverride("admin", toolId, parameterPath, categoryId);
        } catch (Exception e) {
            log.error("Error saving override", e);
            redirectAttributes.addFlashAttribute("error", "Failed to save override: " + e.getMessage());
        }
        return "redirect:/categories/" + categoryId + "/tools/" + toolId + "/overrides";
    }

    @PostMapping("/categories/{categoryId}/tools/{toolId}/overrides/{overrideId}/delete")
    public String deleteToolParameterOverride(@PathVariable String categoryId,
                                               @PathVariable String toolId,
                                               @PathVariable Long overrideId,
                                               RedirectAttributes redirectAttributes) {
        try {
            overrideService.deleteOverride(overrideId);
            redirectAttributes.addFlashAttribute("success", "Override deleted");
            auditLogService.logParameterOverride("admin", toolId, "deleted override #" + overrideId, categoryId);
        } catch (Exception e) {
            log.error("Error deleting override", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete override: " + e.getMessage());
        }
        return "redirect:/categories/" + categoryId + "/tools/" + toolId + "/overrides";
    }

    // Tool-Level Override (when to use, when not to use, etc.)
    @PostMapping("/categories/{categoryId}/tools/{toolId}/tool-override")
    public String saveToolOverride(@PathVariable String categoryId,
                                   @PathVariable String toolId,
                                   @RequestParam(required = false) String whenToUse,
                                   @RequestParam(required = false) String whenNotToUse,
                                   @RequestParam(required = false) String humanReadableDescription,
                                   @RequestParam(required = false) String usageExamples,
                                   @RequestParam(required = false) Integer priorityScore,
                                   RedirectAttributes redirectAttributes) {
        try {
            com.naag.categoryadmin.model.CategoryToolOverride override =
                    com.naag.categoryadmin.model.CategoryToolOverride.builder()
                            .categoryId(categoryId)
                            .toolId(toolId)
                            .whenToUse(whenToUse)
                            .whenNotToUse(whenNotToUse)
                            .humanReadableDescription(humanReadableDescription)
                            .usageExamples(usageExamples)
                            .priorityScore(priorityScore)
                            .active(true)
                            .build();

            overrideService.createOrUpdateToolOverride(override);
            redirectAttributes.addFlashAttribute("success", "Tool selection guidance saved");
            auditLogService.logToolUpdate("admin", toolId, "Tool guidance updated for category: " + categoryId);
        } catch (Exception e) {
            log.error("Error saving tool override", e);
            redirectAttributes.addFlashAttribute("error", "Failed to save tool guidance: " + e.getMessage());
        }
        return "redirect:/categories/" + categoryId + "/tools/" + toolId + "/overrides";
    }

    @PostMapping("/categories/{categoryId}/tools/{toolId}/tool-override/delete")
    public String deleteToolOverride(@PathVariable String categoryId,
                                     @PathVariable String toolId,
                                     RedirectAttributes redirectAttributes) {
        try {
            overrideService.deleteToolOverride(categoryId, toolId);
            redirectAttributes.addFlashAttribute("success", "Tool selection guidance cleared");
        } catch (Exception e) {
            log.error("Error deleting tool override", e);
            redirectAttributes.addFlashAttribute("error", "Failed to clear tool guidance: " + e.getMessage());
        }
        return "redirect:/categories/" + categoryId + "/tools/" + toolId + "/overrides";
    }

    @GetMapping("/categories/new")
    public String newCategoryForm(Model model) {
        model.addAttribute("activePage", "categories");
        model.addAttribute("category", new Category());
        return "categories/form";
    }

    @PostMapping("/categories")
    public String createCategory(@ModelAttribute Category category, RedirectAttributes redirectAttributes) {
        try {
            categoryService.createCategory(category);
            redirectAttributes.addFlashAttribute("success", "Category created successfully");
            auditLogService.logCategoryCreate("admin", category.getId(), category.getName());
        } catch (Exception e) {
            log.error("Error creating category", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create category: " + e.getMessage());
        }
        return "redirect:/categories";
    }

    @GetMapping("/categories/{id}/edit")
    public String editCategoryForm(@PathVariable String id, Model model) {
        model.addAttribute("activePage", "categories");
        categoryService.getCategory(id).ifPresent(category -> model.addAttribute("category", category));
        return "categories/form";
    }

    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable String id, @ModelAttribute Category category,
                                  RedirectAttributes redirectAttributes) {
        try {
            categoryService.updateCategory(id, category);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully");
            auditLogService.logCategoryUpdate("admin", id, category.getName());
        } catch (Exception e) {
            log.error("Error updating category", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update category: " + e.getMessage());
        }
        return "redirect:/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable String id, RedirectAttributes redirectAttributes) {
        String categoryName = null;
        try {
            // Get category name before deletion for audit
            categoryName = categoryService.getCategory(id)
                    .map(Category::getName)
                    .orElse(id);
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("success", "Category deleted successfully");
            auditLogService.logCategoryDelete("admin", id, categoryName);
        } catch (Exception e) {
            log.error("Error deleting category", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete category: " + e.getMessage());
        }
        return "redirect:/categories";
    }

    // Tool Management
    @GetMapping("/tools")
    public String listTools(Model model) {
        model.addAttribute("activePage", "tools");
        model.addAttribute("tools", toolRegistryClient.getAllTools());
        List<Category> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);

        // Build a map of toolId -> list of category names for display
        Map<String, List<String>> toolCategoryMap = new java.util.HashMap<>();
        for (Category cat : categories) {
            if (cat.getToolIds() != null) {
                for (String toolId : cat.getToolIds()) {
                    toolCategoryMap.computeIfAbsent(toolId, k -> new java.util.ArrayList<>()).add(cat.getName());
                }
            }
        }
        model.addAttribute("toolCategoryMap", toolCategoryMap);

        return "tools/list";
    }

    @GetMapping("/tools/new")
    public String newToolForm(Model model) {
        model.addAttribute("activePage", "tools");
        model.addAttribute("tool", new Tool());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "tools/form";
    }

    @PostMapping("/tools")
    public String createTool(@ModelAttribute Tool tool, RedirectAttributes redirectAttributes) {
        try {
            // Auto-generate tool ID from name if not provided
            if (tool.getId() == null || tool.getId().isBlank()) {
                if (tool.getName() == null || tool.getName().isBlank()) {
                    redirectAttributes.addFlashAttribute("error", "Tool name is required");
                    return "redirect:/tools/new";
                }
                // Generate ID from name: lowercase, replace spaces/special chars with underscore
                String generatedId = tool.getName()
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]+", "_")  // Replace non-alphanumeric with underscore
                        .replaceAll("^_+|_+$", "")      // Trim leading/trailing underscores
                        .replaceAll("_+", "_");          // Collapse multiple underscores
                tool.setId(generatedId);
            }

            // Check if tool ID already exists
            Optional<Tool> existing = toolRegistryClient.getTool(tool.getId());
            if (existing.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Tool ID '" + tool.getId() + "' already exists. Please use a different name or ID.");
                return "redirect:/tools/new";
            }

            toolRegistryClient.createTool(tool);
            redirectAttributes.addFlashAttribute("success", "Tool created successfully with ID: " + tool.getId());
            auditLogService.logToolRegister("admin", tool.getId(), tool.getName(), null);
        } catch (Exception e) {
            log.error("Error creating tool", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create tool: " + e.getMessage());
        }
        return "redirect:/tools";
    }

    @GetMapping("/tools/{id}/edit")
    public String editToolForm(@PathVariable String id, Model model) {
        model.addAttribute("activePage", "tools");
        toolRegistryClient.getTool(id).ifPresent(tool -> model.addAttribute("tool", tool));
        model.addAttribute("categories", categoryService.getAllCategories());
        return "tools/form";
    }

    @PostMapping("/tools/{id}")
    public String updateTool(@PathVariable String id, @ModelAttribute Tool tool,
                              RedirectAttributes redirectAttributes) {
        try {
            toolRegistryClient.updateTool(id, tool);
            redirectAttributes.addFlashAttribute("success", "Tool updated successfully");
            auditLogService.logToolUpdate("admin", id, "Tool: " + tool.getName());
        } catch (Exception e) {
            log.error("Error updating tool", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update tool: " + e.getMessage());
        }
        return "redirect:/tools";
    }

    @PostMapping("/tools/{id}/delete")
    public String deleteTool(@PathVariable String id, RedirectAttributes redirectAttributes) {
        String toolName = null;
        try {
            // Get tool name before deletion for audit
            toolName = toolRegistryClient.getTool(id)
                    .map(Tool::getName)
                    .orElse(id);
            toolRegistryClient.deleteTool(id);
            redirectAttributes.addFlashAttribute("success", "Tool deleted successfully");
            auditLogService.logToolDelete("admin", id, toolName);
        } catch (Exception e) {
            log.error("Error deleting tool", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete tool: " + e.getMessage());
        }
        return "redirect:/tools";
    }

    // Detailed Tool Edit with Parameters
    @GetMapping("/tools/{id}/details")
    public String editToolDetailsForm(@PathVariable String id, Model model) {
        model.addAttribute("activePage", "tools");
        try {
            toolRegistryClient.getToolDetails(id).ifPresentOrElse(
                tool -> {
                    // Convert JsonNode to Map for Thymeleaf compatibility
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> toolMap = new com.fasterxml.jackson.databind.ObjectMapper()
                                .convertValue(tool, Map.class);
                        model.addAttribute("tool", toolMap);
                        log.info("Loaded tool details for {} with {} parameters",
                                toolMap.get("toolId"),
                                ((java.util.List<?>) toolMap.get("parameters")).size());
                    } catch (Exception e) {
                        log.error("Error converting tool to map", e);
                        model.addAttribute("tool", tool);
                    }
                },
                () -> {
                    log.warn("Tool not found: {}", id);
                    model.addAttribute("error", "Tool not found: " + id);
                }
            );
        } catch (Exception e) {
            log.error("Error loading tool details for {}", id, e);
            model.addAttribute("error", "Error loading tool: " + e.getMessage());
        }
        model.addAttribute("categories", categoryService.getAllCategories());
        return "tools/edit-details";
    }

    @PostMapping("/tools/{id}/details")
    public String updateToolDetails(@PathVariable String id,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String humanReadableDescription,
                                    @RequestParam(required = false) String categoryId,
                                    RedirectAttributes redirectAttributes) {
        try {
            toolRegistryClient.updateToolDetails(id, description, humanReadableDescription, categoryId);
            redirectAttributes.addFlashAttribute("success", "Tool details updated successfully");
        } catch (Exception e) {
            log.error("Error updating tool details", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update tool details: " + e.getMessage());
        }
        return "redirect:/tools/" + id + "/details";
    }

    @PostMapping("/tools/{id}/parameters")
    public String updateParameter(@PathVariable String id,
                                  @RequestParam Long parameterId,
                                  @RequestParam(required = false) String humanReadableDescription,
                                  @RequestParam(required = false) String example,
                                  @RequestParam(required = false) String enumValues,
                                  RedirectAttributes redirectAttributes) {
        try {
            java.util.List<String> enumList = null;
            if (enumValues != null && !enumValues.isBlank()) {
                enumList = java.util.Arrays.asList(enumValues.split(","));
            }
            toolRegistryClient.updateParameter(id, parameterId, humanReadableDescription, example, enumList);
            redirectAttributes.addFlashAttribute("success", "Parameter updated successfully");
        } catch (Exception e) {
            log.error("Error updating parameter", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update parameter: " + e.getMessage());
        }
        return "redirect:/tools/" + id + "/details";
    }

    @PostMapping("/tools/{id}/responses")
    public String updateResponse(@PathVariable String id,
                                 @RequestParam Long responseId,
                                 @RequestParam(required = false) String humanReadableDescription,
                                 RedirectAttributes redirectAttributes) {
        try {
            toolRegistryClient.updateResponse(id, responseId, humanReadableDescription);
            redirectAttributes.addFlashAttribute("success", "Response updated successfully");
        } catch (Exception e) {
            log.error("Error updating response", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update response: " + e.getMessage());
        }
        return "redirect:/tools/" + id + "/details";
    }

    // Bulk Edit Parameters
    @GetMapping("/tools/{id}/bulk-edit")
    public String bulkEditParametersForm(@PathVariable String id, Model model) {
        model.addAttribute("activePage", "tools");
        try {
            toolRegistryClient.getToolDetails(id).ifPresentOrElse(
                tool -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> toolMap = new com.fasterxml.jackson.databind.ObjectMapper()
                                .convertValue(tool, Map.class);
                        model.addAttribute("tool", toolMap);
                    } catch (Exception e) {
                        log.error("Error converting tool to map", e);
                        model.addAttribute("tool", tool);
                    }
                },
                () -> {
                    model.addAttribute("error", "Tool not found: " + id);
                }
            );
        } catch (Exception e) {
            log.error("Error loading tool for bulk edit", e);
            model.addAttribute("error", "Failed to load tool: " + e.getMessage());
        }
        return "tools/bulk-edit-params";
    }

    @PostMapping("/tools/{id}/bulk-edit")
    public String bulkEditParameters(@PathVariable String id,
                                     @RequestParam Map<String, String> allParams,
                                     RedirectAttributes redirectAttributes) {
        try {
            int updatedCount = 0;

            // Process all parameters from the form
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();

                // Extract parameter updates - format: inputParams[0].id, inputParams[0].humanReadableDescription, etc.
                if (key.contains("].id")) {
                    String prefix = key.substring(0, key.indexOf("].id") + 1);
                    String idStr = entry.getValue();

                    if (idStr != null && !idStr.isBlank()) {
                        Long paramId = Long.parseLong(idStr);
                        String humanDesc = allParams.get(prefix + ".humanReadableDescription");
                        String example = allParams.get(prefix + ".example");
                        String enumValues = allParams.get(prefix + ".enumValues");

                        // Only update if at least one field has a non-blank value
                        boolean hasHumanDesc = humanDesc != null && !humanDesc.isBlank();
                        boolean hasExample = example != null && !example.isBlank();
                        boolean hasEnumValues = enumValues != null && !enumValues.isBlank();

                        if (hasHumanDesc || hasExample || hasEnumValues) {
                            java.util.List<String> enumList = hasEnumValues
                                    ? java.util.Arrays.asList(enumValues.split(","))
                                    : null;

                            toolRegistryClient.updateParameter(id, paramId,
                                    hasHumanDesc ? humanDesc : "",
                                    hasExample ? example : "",
                                    enumList);
                            updatedCount++;
                        }
                    }
                }
            }

            redirectAttributes.addFlashAttribute("success",
                    "Bulk update completed. " + updatedCount + " parameters updated.");
        } catch (Exception e) {
            log.error("Error in bulk parameter update", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update parameters: " + e.getMessage());
        }
        return "redirect:/tools/" + id + "/bulk-edit";
    }

    // OpenAPI Registration
    @GetMapping("/tools/register-openapi")
    public String registerOpenApiForm(Model model) {
        model.addAttribute("activePage", "tools");
        model.addAttribute("categories", categoryService.getAllCategories());
        return "tools/register-openapi";
    }

    // Single Tool Registration
    @GetMapping("/tools/register-single")
    public String registerSingleToolForm(Model model) {
        model.addAttribute("activePage", "tools");
        model.addAttribute("categories", categoryService.getAllCategories());
        return "tools/register-single";
    }

    @PostMapping("/tools/register-single/preview")
    public String previewSingleTool(
            @RequestParam String toolId,
            @RequestParam String openApiEndpoint,
            @RequestParam String path,
            @RequestParam String httpMethod,
            @RequestParam(required = false) String categoryId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("activePage", "tools");

            // Preview the tool
            JsonNode preview = toolRegistryClient.previewTool(toolId, openApiEndpoint, path, httpMethod);

            // Create a request object to pass to template
            var request = new java.util.HashMap<String, String>();
            request.put("toolId", toolId);
            request.put("openApiEndpoint", openApiEndpoint);
            request.put("path", path);
            request.put("httpMethod", httpMethod);
            request.put("categoryId", categoryId);

            model.addAttribute("request", request);
            model.addAttribute("preview", preview);
            model.addAttribute("categories", categoryService.getAllCategories());

            return "tools/preview";
        } catch (Exception e) {
            log.error("Error previewing tool", e);
            redirectAttributes.addFlashAttribute("error", "Failed to preview tool: " + e.getMessage());
            return "redirect:/tools/register-single";
        }
    }

    @PostMapping("/tools/register-single/preview-file")
    public String previewSingleToolFromFile(
            @RequestParam String toolId,
            @RequestParam("openApiFile") org.springframework.web.multipart.MultipartFile openApiFile,
            @RequestParam String path,
            @RequestParam String httpMethod,
            @RequestParam String baseUrl,
            @RequestParam(required = false) String categoryId,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("activePage", "tools");

            // Read file content
            String openApiContent = new String(openApiFile.getBytes(), java.nio.charset.StandardCharsets.UTF_8);

            // Preview the tool from content
            JsonNode preview = toolRegistryClient.previewToolFromContent(toolId, openApiContent, path, httpMethod);

            // Create a request object to pass to template
            var request = new java.util.HashMap<String, String>();
            request.put("toolId", toolId);
            request.put("openApiEndpoint", "file:" + openApiFile.getOriginalFilename());
            request.put("path", path);
            request.put("httpMethod", httpMethod);
            request.put("categoryId", categoryId);
            request.put("baseUrl", baseUrl);
            request.put("openApiContent", openApiContent);

            model.addAttribute("request", request);
            model.addAttribute("preview", preview);
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("sourceType", "file");

            return "tools/preview";
        } catch (Exception e) {
            log.error("Error previewing tool from file", e);
            redirectAttributes.addFlashAttribute("error", "Failed to preview tool: " + e.getMessage());
            return "redirect:/tools/register-single";
        }
    }

    @PostMapping("/tools/register-single/confirm")
    public String confirmSingleToolRegistration(
            @RequestParam String toolId,
            @RequestParam String openApiEndpoint,
            @RequestParam String path,
            @RequestParam String httpMethod,
            @RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String humanReadableDescription,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String openApiContent,
            @RequestParam(required = false) String[] paramNames,
            @RequestParam(required = false) Integer[] paramNestingLevels,
            @RequestParam(required = false) String[] paramHumanDescriptions,
            @RequestParam(required = false) String[] paramExamples,
            @RequestParam(required = false) String[] responseStatusCodes,
            @RequestParam(required = false) String[] responseHumanDescriptions,
            @RequestParam(required = false) String[] responseParamNames,
            @RequestParam(required = false) Integer[] responseParamNestingLevels,
            @RequestParam(required = false) String[] responseParamStatusCodes,
            @RequestParam(required = false) String[] responseParamHumanDescriptions,
            @RequestParam(required = false) String[] responseParamExamples,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            // Build the registration request with all parameters
            Map<String, Object> registrationRequest = new java.util.HashMap<>();
            registrationRequest.put("toolId", toolId);
            registrationRequest.put("openApiEndpoint", openApiEndpoint);
            registrationRequest.put("path", path);
            registrationRequest.put("httpMethod", httpMethod);
            if (baseUrl != null && !baseUrl.isEmpty()) {
                registrationRequest.put("baseUrl", baseUrl);
            }
            if (humanReadableDescription != null && !humanReadableDescription.isEmpty()) {
                registrationRequest.put("humanReadableDescription", humanReadableDescription);
            }
            if (categoryId != null && !categoryId.isEmpty()) {
                registrationRequest.put("categoryId", categoryId);
            }
            if (paramNames != null && paramNames.length > 0) {
                registrationRequest.put("paramNames", java.util.Arrays.asList(paramNames));
                if (paramNestingLevels != null) {
                    registrationRequest.put("paramNestingLevels", java.util.Arrays.asList(paramNestingLevels));
                }
                if (paramHumanDescriptions != null) {
                    registrationRequest.put("paramHumanDescriptions", java.util.Arrays.asList(paramHumanDescriptions));
                }
                if (paramExamples != null) {
                    registrationRequest.put("paramExamples", java.util.Arrays.asList(paramExamples));
                }
            }
            if (responseStatusCodes != null && responseStatusCodes.length > 0) {
                registrationRequest.put("responseStatusCodes", java.util.Arrays.asList(responseStatusCodes));
                if (responseHumanDescriptions != null) {
                    registrationRequest.put("responseHumanDescriptions", java.util.Arrays.asList(responseHumanDescriptions));
                }
            }
            if (responseParamNames != null && responseParamNames.length > 0) {
                registrationRequest.put("responseParamNames", java.util.Arrays.asList(responseParamNames));
                if (responseParamNestingLevels != null) {
                    registrationRequest.put("responseParamNestingLevels", java.util.Arrays.asList(responseParamNestingLevels));
                }
                if (responseParamStatusCodes != null) {
                    registrationRequest.put("responseParamStatusCodes", java.util.Arrays.asList(responseParamStatusCodes));
                }
                if (responseParamHumanDescriptions != null) {
                    registrationRequest.put("responseParamHumanDescriptions", java.util.Arrays.asList(responseParamHumanDescriptions));
                }
                if (responseParamExamples != null) {
                    registrationRequest.put("responseParamExamples", java.util.Arrays.asList(responseParamExamples));
                }
            }

            JsonNode result;
            // Check if this is a file-based registration (openApiContent is present)
            if (openApiContent != null && !openApiContent.isEmpty()) {
                registrationRequest.put("openApiContent", openApiContent);
                result = toolRegistryClient.registerSingleToolFromContent(registrationRequest);
            } else {
                result = toolRegistryClient.registerSingleTool(registrationRequest);
            }

            redirectAttributes.addFlashAttribute("success", "Tool '" + toolId + "' registered successfully");
            auditLogService.logToolRegister("admin", toolId, toolId, categoryId);
            return "redirect:/tools";
        } catch (Exception e) {
            log.error("Error registering tool", e);

            // On failure, re-render the preview page with all data preserved
            model.addAttribute("activePage", "tools");
            model.addAttribute("error", "Failed to register tool: " + e.getMessage());

            // Recreate the request map
            var request = new java.util.HashMap<String, String>();
            request.put("toolId", toolId);
            request.put("openApiEndpoint", openApiEndpoint);
            request.put("path", path);
            request.put("httpMethod", httpMethod);
            request.put("categoryId", categoryId);
            request.put("baseUrl", baseUrl);
            // Preserve OpenAPI content for file-based registration retry
            if (openApiContent != null && !openApiContent.isEmpty()) {
                request.put("openApiContent", openApiContent);
            }
            model.addAttribute("request", request);

            // Try to re-fetch the preview data
            try {
                JsonNode preview;
                if (openApiContent != null && !openApiContent.isEmpty()) {
                    // For file-based registration, use the preserved content
                    preview = toolRegistryClient.previewToolFromContent(toolId, openApiContent, path, httpMethod);
                } else {
                    preview = toolRegistryClient.previewTool(toolId, openApiEndpoint, path, httpMethod);
                }
                model.addAttribute("preview", preview);
                model.addAttribute("categories", categoryService.getAllCategories());
                return "tools/preview";
            } catch (Exception ex) {
                log.error("Error re-fetching preview", ex);
                redirectAttributes.addFlashAttribute("error", "Failed to register tool: " + e.getMessage());
                return "redirect:/tools/register-single";
            }
        }
    }

    // API endpoints for single tool registration
    @GetMapping("/api/tools/openapi/paths")
    @ResponseBody
    public JsonNode getOpenApiPaths(@RequestParam String openApiUrl) {
        try {
            return toolRegistryClient.getOpenApiPaths(openApiUrl);
        } catch (Exception e) {
            log.error("Error fetching OpenAPI paths", e);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.createObjectNode().put("error", e.getMessage());
        }
    }

    @GetMapping("/api/tools/check-duplicate")
    @ResponseBody
    public java.util.Map<String, Boolean> checkDuplicate(
            @RequestParam String openApiEndpoint,
            @RequestParam String path,
            @RequestParam String httpMethod) {
        try {
            boolean exists = toolRegistryClient.checkDuplicate(openApiEndpoint, path, httpMethod);
            return java.util.Map.of("exists", exists);
        } catch (Exception e) {
            log.error("Error checking duplicate", e);
            return java.util.Map.of("exists", false);
        }
    }

    @PostMapping("/api/tools/parse-openapi-content")
    @ResponseBody
    public JsonNode parseOpenApiContent(@RequestBody java.util.Map<String, String> request) {
        try {
            String content = request.get("content");
            if (content == null || content.isBlank()) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.createObjectNode().put("error", "content is required");
            }
            return toolRegistryClient.parseOpenApiContent(content);
        } catch (Exception e) {
            log.error("Error parsing OpenAPI content", e);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.createObjectNode().put("error", e.getMessage());
        }
    }

    @PostMapping("/tools/register-openapi")
    public String registerFromOpenApi(@RequestParam String openApiUrl,
                                       @RequestParam(required = false) String categoryId,
                                       RedirectAttributes redirectAttributes) {
        try {
            var result = toolRegistryClient.registerFromOpenApi(openApiUrl, categoryId);
            redirectAttributes.addFlashAttribute("success", "Tools registered from OpenAPI: " + result.toString());
            int toolCount = result.has("count") ? result.get("count").asInt() : 0;
            auditLogService.logOpenApiImport("admin", openApiUrl, toolCount, categoryId);
        } catch (Exception e) {
            log.error("Error registering from OpenAPI", e);
            redirectAttributes.addFlashAttribute("error", "Failed to register from OpenAPI: " + e.getMessage());
        }
        return "redirect:/tools";
    }

    @PostMapping("/tools/register-openapi-file")
    public String registerFromOpenApiFile(@RequestParam("openApiFile") MultipartFile file,
                                           @RequestParam String baseUrl,
                                           @RequestParam(required = false) String categoryId,
                                           RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/tools/register-openapi";
            }

            String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String filename = file.getOriginalFilename();

            var result = toolRegistryClient.registerFromOpenApiContent(content, baseUrl, categoryId, filename);
            redirectAttributes.addFlashAttribute("success", "Tools registered from OpenAPI file: " + result.toString());
            int toolCount = result.has("count") ? result.get("count").asInt() : 0;
            auditLogService.logOpenApiImport("admin", filename, toolCount, categoryId);
        } catch (Exception e) {
            log.error("Error registering from OpenAPI file", e);
            redirectAttributes.addFlashAttribute("error", "Failed to register from OpenAPI file: " + e.getMessage());
        }
        return "redirect:/tools";
    }

    // RAG Document Management
    @GetMapping("/documents")
    public String listDocuments(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String category,
                                Model model) {
        model.addAttribute("activePage", "documents");
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentCategory", category);

        // Build category lookup map for displaying names
        List<Category> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("categoryMap", categories.stream()
                .collect(java.util.stream.Collectors.toMap(Category::getId, Category::getName, (a, b) -> a)));

        try {
            model.addAttribute("stats", ragServiceClient.getStats());
            // Use merged documents to show all RAG docs including directly ingested ones
            var allDocs = ragServiceClient.getMergedDocuments(category);
            // Convert JsonNode array to List for Thymeleaf compatibility
            var docsList = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            if (allDocs != null && allDocs.isArray()) {
                for (var doc : allDocs) {
                    docsList.add(doc);
                }
            }
            // Filter by status if specified
            if (status != null && !status.isBlank()) {
                docsList.removeIf(doc -> {
                    String docStatus = doc.has("status") ? doc.get("status").asText() : "";
                    return !status.equals(docStatus);
                });
            }
            model.addAttribute("documents", docsList);
            model.addAttribute("sseEndpoint", ragServiceClient.getSseEndpoint());
        } catch (Exception e) {
            log.warn("Could not fetch RAG stats", e);
            model.addAttribute("documents", java.util.Collections.emptyList());
        }
        return "documents/list";
    }

    @GetMapping("/api/documents/check-title")
    @ResponseBody
    public java.util.Map<String, Object> checkDocumentTitle(@RequestParam String title) {
        try {
            boolean exists = ragServiceClient.checkTitleExists(title);
            return java.util.Map.of("exists", exists, "title", title);
        } catch (Exception e) {
            log.error("Error checking document title", e);
            return java.util.Map.of("exists", false, "error", e.getMessage());
        }
    }

    @GetMapping("/documents/upload")
    public String uploadDocumentForm(@RequestParam(required = false) String categoryId, Model model) {
        model.addAttribute("activePage", "documents");
        model.addAttribute("selectedCategoryId", categoryId);
        if (categoryId != null && !categoryId.isBlank()) {
            categoryService.getCategory(categoryId).ifPresent(cat -> model.addAttribute("selectedCategory", cat));
        }
        model.addAttribute("acceptedFileTypes", documentParserService.getAcceptAttribute());
        model.addAttribute("supportedExtensions", documentParserService.getSupportedExtensions());
        return "documents/upload";
    }

    @PostMapping("/documents/upload")
    public String uploadDocument(@RequestParam(required = false) String docId,
                                  @RequestParam(required = false) String content,
                                  @RequestParam String categoryId,
                                  @RequestParam(required = false) String title,
                                  @RequestParam(required = false) MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        // Validate category is provided
        if (categoryId == null || categoryId.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Category is required. Please select a category.");
            return "redirect:/documents/upload";
        }

        try {
            String textContent = content;
            String documentId = docId;
            String documentTitle = title;

            // If file is uploaded, parse it
            if (file != null && !file.isEmpty()) {
                String filename = file.getOriginalFilename();
                log.info("Processing uploaded file for preview: {}", filename);

                if (!documentParserService.isSupported(filename)) {
                    redirectAttributes.addFlashAttribute("error",
                            "Unsupported file type. Supported: " + documentParserService.getSupportedExtensions());
                    return "redirect:/documents/upload";
                }

                var parseResult = documentParserService.parseDocument(file);
                if (!parseResult.success()) {
                    redirectAttributes.addFlashAttribute("error",
                            "Failed to parse file: " + parseResult.errorMessage());
                    return "redirect:/documents/upload";
                }

                textContent = parseResult.text();

                // Use filename as docId if not provided
                if (documentId == null || documentId.isBlank()) {
                    documentId = filename.replaceAll("[^a-zA-Z0-9.-]", "-")
                            .replaceAll("-+", "-")
                            .toLowerCase();
                }

                // Use filename as title if not provided
                if (documentTitle == null || documentTitle.isBlank()) {
                    documentTitle = filename;
                }

                log.info("Parsed file: {} chars extracted", textContent.length());
            }

            // Validate we have content
            if (textContent == null || textContent.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "No content provided. Upload a file or paste text content.");
                return "redirect:/documents/upload";
            }

            // Validate we have docId
            if (documentId == null || documentId.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Document ID is required");
                return "redirect:/documents/upload";
            }

            var result = ragServiceClient.uploadDocumentWithPreview(documentId, documentTitle, textContent, categoryId);
            String uploadId = result.has("uploadId") ? result.get("uploadId").asText() : "";
            redirectAttributes.addFlashAttribute("success", "Document uploaded. Processing started.");
            redirectAttributes.addFlashAttribute("uploadId", uploadId);

            // Log successful upload
            auditLogService.logDocumentUpload("admin", documentId, documentTitle, categoryId);

            return "redirect:/documents/uploads/" + uploadId;
        } catch (Exception e) {
            log.error("Error uploading document", e);
            // Extract the actual error message, removing wrapper prefixes
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.startsWith("Error uploading document: ")) {
                errorMsg = errorMsg.substring("Error uploading document: ".length());
            }
            redirectAttributes.addFlashAttribute("error", errorMsg);
            return "redirect:/documents/upload";
        }
    }

    @GetMapping("/documents/uploads/{uploadId}")
    public String viewUploadDetails(@PathVariable String uploadId, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("activePage", "documents");
        try {
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details == null) {
                redirectAttributes.addFlashAttribute("error", "Upload not found: " + uploadId);
                return "redirect:/documents";
            }
            model.addAttribute("details", details);
            model.addAttribute("uploadId", uploadId);
            model.addAttribute("sseEndpoint", ragServiceClient.getUploadSseEndpoint(uploadId));
            model.addAttribute("categories", categoryService.getAllCategories());
        } catch (Exception e) {
            log.error("Error fetching upload details for {}", uploadId, e);
            redirectAttributes.addFlashAttribute("error", "Failed to load upload details: " + e.getMessage());
            return "redirect:/documents";
        }
        return "documents/preview";
    }

    @GetMapping("/documents/view/{docId}")
    public String viewDocumentByDocId(@PathVariable String docId, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("activePage", "documents");

        // First try to find the upload by docId
        String uploadId = ragServiceClient.findUploadIdByDocId(docId);
        if (uploadId != null) {
            return "redirect:/documents/uploads/" + uploadId;
        }

        // If no upload found, show the RAG document info
        try {
            var docInfo = ragServiceClient.getDocumentInfo(docId);
            if (docInfo != null) {
                model.addAttribute("document", docInfo);
                model.addAttribute("docId", docId);
                model.addAttribute("categories", categoryService.getAllCategories());
                return "documents/view";
            }
        } catch (Exception e) {
            log.error("Error fetching document info for {}", docId, e);
        }

        redirectAttributes.addFlashAttribute("error", "Document not found: " + docId);
        return "redirect:/documents";
    }

    @PostMapping("/documents/uploads/{uploadId}/approve")
    public String approveUpload(@PathVariable String uploadId, RedirectAttributes redirectAttributes) {
        String docId = null;
        String categoryId = null;
        try {
            // Get upload details for audit logging
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details != null && details.has("upload")) {
                var upload = details.get("upload");
                docId = upload.has("docId") ? upload.get("docId").asText() : uploadId;
                categoryId = upload.has("categoryId") ? upload.get("categoryId").asText() : null;
            }

            var result = ragServiceClient.approveUpload(uploadId);
            if (result.has("success") && result.get("success").asBoolean()) {
                // Ensure RAG tool exists for this category (auto-add on first document)
                if (categoryId != null && categoryService.ensureRagToolForCategory(categoryId)) {
                    log.info("Auto-added RAG tool to category {} on document approval", categoryId);
                }

                redirectAttributes.addFlashAttribute("success", "Document approved and moved to RAG knowledge base");
                auditLogService.logDocumentApprove("admin", docId != null ? docId : uploadId, categoryId);
            } else {
                String error = result.has("error") ? result.get("error").asText() : "Unknown error";
                redirectAttributes.addFlashAttribute("error", "Failed to approve: " + error);
            }
        } catch (Exception e) {
            log.error("Error approving upload", e);
            redirectAttributes.addFlashAttribute("error", "Failed to approve: " + e.getMessage());
        }
        // Redirect back to the same document detail page to show updated status
        return "redirect:/documents/uploads/" + uploadId;
    }

    @PostMapping("/documents/uploads/{uploadId}/retry")
    public String retryUpload(@PathVariable String uploadId, RedirectAttributes redirectAttributes) {
        String docId = null;
        String categoryId = null;
        try {
            // Get upload details for audit logging
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details != null && details.has("upload")) {
                var upload = details.get("upload");
                docId = upload.has("docId") ? upload.get("docId").asText() : uploadId;
                categoryId = upload.has("categoryId") ? upload.get("categoryId").asText() : null;
            }

            ragServiceClient.retryUpload(uploadId);
            redirectAttributes.addFlashAttribute("success", "Reprocessing started");
            auditLogService.logDocumentRetry("admin", docId != null ? docId : uploadId, categoryId);
        } catch (Exception e) {
            log.error("Error retrying upload", e);
            redirectAttributes.addFlashAttribute("error", "Failed to retry: " + e.getMessage());
        }
        return "redirect:/documents/uploads/" + uploadId;
    }

    @PostMapping("/documents/uploads/{uploadId}/delete")
    public String deleteUpload(@PathVariable String uploadId, RedirectAttributes redirectAttributes) {
        String docId = null;
        String categoryId = null;
        try {
            // Get upload details before deletion for audit logging
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details != null && details.has("upload")) {
                var upload = details.get("upload");
                docId = upload.has("docId") ? upload.get("docId").asText() : uploadId;
                categoryId = upload.has("categoryId") ? upload.get("categoryId").asText() : null;
            }

            ragServiceClient.deleteUpload(uploadId);
            redirectAttributes.addFlashAttribute("success", "Upload deleted successfully");

            // Log successful deletion
            auditLogService.logDocumentDelete("admin", docId != null ? docId : uploadId, categoryId);
        } catch (Exception e) {
            log.error("Error deleting upload", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete upload: " + e.getMessage());
        }
        return "redirect:/documents";
    }

    @PostMapping("/documents/delete/{docId}")
    public String deleteRagDocument(@PathVariable String docId, RedirectAttributes redirectAttributes) {
        try {
            ragServiceClient.deleteDocument(docId);
            redirectAttributes.addFlashAttribute("success", "Document '" + docId + "' deleted from RAG");

            // Log successful deletion
            auditLogService.logDocumentDelete("admin", docId, null);
        } catch (Exception e) {
            log.error("Error deleting document from RAG: {}", docId, e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete document: " + e.getMessage());
        }
        return "redirect:/documents";
    }

    @GetMapping("/documents/category/{categoryId}")
    public String listDocumentsByCategory(@PathVariable String categoryId, Model model) {
        model.addAttribute("activePage", "documents");

        // Get category details
        categoryService.getCategory(categoryId).ifPresent(cat -> model.addAttribute("category", cat));

        // Get documents for this category
        try {
            model.addAttribute("documents", ragServiceClient.getDocumentsByCategory(categoryId));
        } catch (Exception e) {
            log.warn("Could not fetch documents for category {}", categoryId, e);
        }

        // Also get uploads for this category
        try {
            var allUploads = ragServiceClient.getDocumentUploads(null);
            if (allUploads != null && allUploads.isArray()) {
                var filteredUploads = new java.util.ArrayList<>();
                for (var upload : allUploads) {
                    if (upload.has("categoryId") && categoryId.equals(upload.get("categoryId").asText())) {
                        filteredUploads.add(upload);
                    }
                }
                model.addAttribute("uploads", filteredUploads);
            }
        } catch (Exception e) {
            log.warn("Could not fetch uploads for category {}", categoryId, e);
        }

        return "documents/category";
    }

    // Direct ingest (bypasses preview) - supports file upload OR text input
    @GetMapping("/documents/ingest")
    public String ingestDocumentForm(@RequestParam(required = false) String categoryId, Model model) {
        model.addAttribute("activePage", "documents");
        model.addAttribute("selectedCategoryId", categoryId);
        if (categoryId != null && !categoryId.isBlank()) {
            categoryService.getCategory(categoryId).ifPresent(cat -> model.addAttribute("selectedCategory", cat));
        }
        model.addAttribute("acceptedFileTypes", documentParserService.getAcceptAttribute());
        model.addAttribute("supportedExtensions", documentParserService.getSupportedExtensions());
        return "documents/ingest";
    }

    @PostMapping("/documents/ingest")
    public String ingestDocument(@RequestParam(required = false) String docId,
                                  @RequestParam(required = false) String content,
                                  @RequestParam String categoryId,
                                  @RequestParam(required = false) String title,
                                  @RequestParam(required = false) MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        // Validate category is provided
        if (categoryId == null || categoryId.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Category is required. Please select a category.");
            return "redirect:/documents/ingest";
        }

        try {
            String textContent = content;
            String documentId = docId;
            String documentTitle = title;
            String documentType = "text";

            // If file is uploaded, parse it
            if (file != null && !file.isEmpty()) {
                String filename = file.getOriginalFilename();
                log.info("Processing uploaded file: {}", filename);

                if (!documentParserService.isSupported(filename)) {
                    redirectAttributes.addFlashAttribute("error",
                            "Unsupported file type. Supported: " + documentParserService.getSupportedExtensions());
                    return "redirect:/documents/ingest";
                }

                var parseResult = documentParserService.parseDocument(file);
                if (!parseResult.success()) {
                    redirectAttributes.addFlashAttribute("error",
                            "Failed to parse file: " + parseResult.errorMessage());
                    return "redirect:/documents/ingest";
                }

                textContent = parseResult.text();
                documentType = documentParserService.getDocumentType(filename);

                // Use filename as docId if not provided
                if (documentId == null || documentId.isBlank()) {
                    documentId = filename.replaceAll("[^a-zA-Z0-9.-]", "-")
                            .replaceAll("-+", "-")
                            .toLowerCase();
                }

                // Use filename as title if not provided
                if (documentTitle == null || documentTitle.isBlank()) {
                    documentTitle = filename;
                }

                log.info("Parsed {} file: {} chars extracted", documentType, textContent.length());
            }

            // Validate we have content
            if (textContent == null || textContent.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "No content provided. Upload a file or paste text content.");
                return "redirect:/documents/ingest";
            }

            // Validate we have docId
            if (documentId == null || documentId.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Document ID is required");
                return "redirect:/documents/ingest";
            }

            var metadata = new java.util.HashMap<String, Object>();
            if (documentTitle != null && !documentTitle.isBlank()) {
                metadata.put("title", documentTitle);
            }
            metadata.put("documentType", documentType);

            ragServiceClient.ingestDocument(documentId, textContent, categoryId, metadata);

            // Ensure RAG tool exists for this category (auto-add on first document)
            if (categoryService.ensureRagToolForCategory(categoryId)) {
                log.info("Auto-added RAG tool to category {} on first document ingest", categoryId);
            }

            redirectAttributes.addFlashAttribute("success",
                    "Document ingested successfully (" + textContent.length() + " characters)");

            // Log successful ingest
            auditLogService.logDocumentUpload("admin", documentId, documentTitle, categoryId);
        } catch (Exception e) {
            log.error("Error ingesting document", e);
            redirectAttributes.addFlashAttribute("error", "Failed to ingest document: " + e.getMessage());
        }
        return "redirect:/documents";
    }

    @PostMapping("/documents/{docId}/delete")
    public String deleteDocument(@PathVariable String docId, RedirectAttributes redirectAttributes) {
        try {
            ragServiceClient.deleteDocument(docId);
            redirectAttributes.addFlashAttribute("success", "Document deleted successfully");

            // Log successful deletion
            auditLogService.logDocumentDelete("admin", docId, null);
        } catch (Exception e) {
            log.error("Error deleting document", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete document: " + e.getMessage());
        }
        return "redirect:/documents";
    }

    // Health Check
    @GetMapping("/health")
    @ResponseBody
    public java.util.Map<String, Object> health() {
        return java.util.Map.of(
                "status", "UP",
                "service", "naag-category-admin"
        );
    }

    // Setup Data Management
    @GetMapping("/setup")
    public String setupPage(Model model) {
        model.addAttribute("activePage", "setup");
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("tools", toolRegistryClient.getAllTools());
        return "setup/index";
    }

    @PostMapping("/setup/initialize")
    public String runSetupInitialization(RedirectAttributes redirectAttributes) {
        try {
            log.info("Manual setup initialization triggered");
            setupDataInitializer.initializeSetupData();
            auditLogService.logSetupRun("admin");
            redirectAttributes.addFlashAttribute("success",
                "Setup initialization started. Documents will be uploaded and APIs will be registered in the background.");
        } catch (Exception e) {
            log.error("Error running setup initialization", e);
            redirectAttributes.addFlashAttribute("error", "Failed to run setup: " + e.getMessage());
        }
        return "redirect:/setup";
    }

    // Audit Trail Dashboard
    @GetMapping("/audit")
    public String auditDashboard(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "admin") String source,
            Model model) {

        model.addAttribute("activePage", "audit");
        model.addAttribute("currentSource", source);

        if ("chat".equals(source)) {
            // Fetch chat app audit logs
            List<JsonNode> chatLogs = chatAppClient.getAuditLogs(userId, page, size);
            model.addAttribute("chatAuditLogs", chatLogs);
            model.addAttribute("totalItems", chatLogs.size());
            model.addAttribute("chatAppAvailable", chatAppClient.isAvailable());

            // Get chat user stats
            try {
                Map<String, Object> chatStats = chatAppClient.getUserStats(userId);
                model.addAttribute("chatStats", chatStats);
            } catch (Exception e) {
                log.warn("Could not fetch chat stats", e);
            }
        } else {
            // Get admin audit logs with filters
            Page<AuditLog> auditLogs = auditLogService.searchAuditLogs(
                    userId, action, entityType, categoryId, startDate, endDate, page, size);

            model.addAttribute("auditLogs", auditLogs);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", auditLogs.getTotalPages());
            model.addAttribute("totalItems", auditLogs.getTotalElements());

            // Statistics for admin logs
            model.addAttribute("stats24h", auditLogService.getStatistics(24));
        }

        // Get filter options
        model.addAttribute("userIds", auditLogService.getDistinctUserIds());
        model.addAttribute("actions", auditLogService.getDistinctActions());
        model.addAttribute("entityTypes", auditLogService.getDistinctEntityTypes());
        model.addAttribute("categories", categoryService.getAllCategories());

        // Current filter values
        model.addAttribute("filterUserId", userId);
        model.addAttribute("filterAction", action);
        model.addAttribute("filterEntityType", entityType);
        model.addAttribute("filterCategoryId", categoryId);
        model.addAttribute("filterStartDate", startDate);
        model.addAttribute("filterEndDate", endDate);

        // Check if chat app is available
        model.addAttribute("chatAppAvailable", chatAppClient.isAvailable());

        return "audit/index";
    }

    @GetMapping("/audit/chat")
    public String chatAuditDashboard(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Integer lastMinutes,
            @RequestParam(required = false) Integer lastHours,
            @RequestParam(required = false) String timeRange,
            Model model) {

        model.addAttribute("activePage", "audit");
        model.addAttribute("currentSource", "chat");

        // Parse timeRange quick select (Kibana-style)
        if (timeRange != null && !timeRange.isEmpty()) {
            switch (timeRange) {
                case "15m" -> lastMinutes = 15;
                case "30m" -> lastMinutes = 30;
                case "1h" -> lastHours = 1;
                case "4h" -> lastHours = 4;
                case "12h" -> lastHours = 12;
                case "24h" -> lastHours = 24;
                case "7d" -> lastHours = 24 * 7;
                case "30d" -> lastHours = 24 * 30;
                // "all" or unknown = no filter
            }
        }

        // Check if chat app is available
        boolean chatAppAvailable = chatAppClient.isAvailable();
        model.addAttribute("chatAppAvailable", chatAppAvailable);

        if (chatAppAvailable) {
            // Fetch chat app audit logs with time-based filtering
            List<JsonNode> chatLogs = chatAppClient.getAuditLogs(userId, page, size, lastMinutes, lastHours);
            model.addAttribute("chatAuditLogs", chatLogs);
            model.addAttribute("totalItems", chatLogs.size());

            // Get chat user stats
            try {
                Map<String, Object> chatStats = chatAppClient.getUserStats(userId);
                model.addAttribute("chatStats", chatStats);
            } catch (Exception e) {
                log.warn("Could not fetch chat stats", e);
            }

            // Get failed operations
            try {
                List<JsonNode> failedOps = chatAppClient.getFailedOperations(userId);
                model.addAttribute("failedOperations", failedOps);
            } catch (Exception e) {
                log.warn("Could not fetch failed operations", e);
            }
        }

        model.addAttribute("filterUserId", userId);
        model.addAttribute("filterTimeRange", timeRange != null ? timeRange : "all");
        model.addAttribute("filterLastMinutes", lastMinutes);
        model.addAttribute("filterLastHours", lastHours);
        model.addAttribute("categories", categoryService.getAllCategories());

        return "audit/chat";
    }

    @GetMapping("/audit/stats")
    @ResponseBody
    public java.util.Map<String, Object> auditStats(@RequestParam(defaultValue = "24") int hours) {
        return auditLogService.getStatistics(hours);
    }

    @GetMapping("/audit/chat/api")
    @ResponseBody
    public List<JsonNode> getChatAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return chatAppClient.getAuditLogs(userId, page, size);
    }

    @GetMapping("/audit/export")
    @ResponseBody
    public List<AuditLog> exportAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        Page<AuditLog> logs = auditLogService.searchAuditLogs(
                userId, action, entityType, categoryId, startDate, endDate, 0, 10000);
        return logs.getContent();
    }

    // ==================== FAQ Management ====================

    /**
     * FAQ Management page - list and manage all FAQs
     */
    @GetMapping("/faq")
    public String faqManagement(Model model) {
        model.addAttribute("activePage", "faq");
        model.addAttribute("ragServiceUrl", ragServiceClient.getBaseUrl());
        model.addAttribute("categoryAdminUrl", "http://localhost:8085");
        return "faq";
    }

    /**
     * FAQ Review page - review and approve Q&A pairs for a document upload
     */
    @GetMapping("/faq/review/{uploadId}")
    public String faqReview(@PathVariable String uploadId, Model model) {
        model.addAttribute("activePage", "faq");
        model.addAttribute("ragServiceUrl", ragServiceClient.getBaseUrl());

        try {
            // Get upload details
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details == null) {
                return "redirect:/documents";
            }

            // Extract upload info
            JsonNode upload = details.get("upload");
            model.addAttribute("upload", Map.of(
                    "id", upload.get("id").asText(),
                    "docId", upload.get("docId").asText(),
                    "title", upload.has("title") ? upload.get("title").asText() : "Untitled",
                    "status", upload.get("status").asText(),
                    "categoryId", upload.has("categoryId") ? upload.get("categoryId").asText() : ""
            ));

            // Extract Q&A pairs
            JsonNode qaPairs = details.get("qaPairs");
            if (qaPairs != null && qaPairs.isArray()) {
                var qaList = new java.util.ArrayList<Map<String, Object>>();
                for (JsonNode qa : qaPairs) {
                    var qaMap = new java.util.HashMap<String, Object>();
                    qaMap.put("id", qa.get("id").asLong());
                    qaMap.put("question", qa.get("question").asText());
                    qaMap.put("expectedAnswer", qa.get("expectedAnswer").asText());
                    qaMap.put("ragAnswer", qa.has("ragAnswer") && !qa.get("ragAnswer").isNull() ? qa.get("ragAnswer").asText() : null);
                    qaMap.put("similarityScore", qa.has("similarityScore") && !qa.get("similarityScore").isNull() ? qa.get("similarityScore").asDouble() : null);
                    qaMap.put("validationStatus", qa.has("validationStatus") ? qa.get("validationStatus").asText() : "PENDING");
                    qaMap.put("questionType", qa.has("questionType") ? qa.get("questionType").asText() : "FINE_GRAIN");
                    qaMap.put("selectedForFaq", qa.has("selectedForFaq") && qa.get("selectedForFaq").asBoolean());
                    qaMap.put("selectedAnswerSource", qa.has("selectedAnswerSource") && !qa.get("selectedAnswerSource").isNull() ? qa.get("selectedAnswerSource").asText() : null);
                    qaMap.put("editedAnswer", qa.has("editedAnswer") && !qa.get("editedAnswer").isNull() ? qa.get("editedAnswer").asText() : null);
                    qaMap.put("faqStatus", qa.has("faqStatus") ? qa.get("faqStatus").asText() : "PENDING");
                    qaList.add(qaMap);
                }
                model.addAttribute("qaPairs", qaList);
            } else {
                model.addAttribute("qaPairs", java.util.Collections.emptyList());
            }

        } catch (Exception e) {
            log.error("Error fetching upload details for FAQ review", e);
            model.addAttribute("error", "Failed to load upload details: " + e.getMessage());
            model.addAttribute("upload", Map.of("id", uploadId, "title", "Unknown", "status", "ERROR"));
            model.addAttribute("qaPairs", java.util.Collections.emptyList());
        }

        return "faq-review";
    }

    /**
     * User Questions Analysis page
     */
    @GetMapping("/user-questions")
    public String userQuestionsAnalysis(Model model) {
        model.addAttribute("activePage", "user-questions");
        model.addAttribute("ragServiceUrl", ragServiceClient.getBaseUrl());
        model.addAttribute("categoryAdminUrl", "http://localhost:8085");
        model.addAttribute("chatAppUrl", chatAppClient.getBaseUrl());
        return "user-questions";
    }

    // ==================== Document Features (Generate More Q&A, Chat, FAQ) ====================

    /**
     * Generate more Q&A pairs for a document.
     */
    @PostMapping("/documents/uploads/{uploadId}/generate-qa")
    @ResponseBody
    public Map<String, Object> generateMoreQA(
            @PathVariable String uploadId,
            @RequestParam(defaultValue = "3") int fineGrainCount,
            @RequestParam(defaultValue = "2") int summaryCount) {
        try {
            var result = ragServiceClient.generateMoreQA(uploadId, fineGrainCount, summaryCount);

            // Get docId for audit logging
            String docId = uploadId;
            String categoryId = null;
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details != null && details.has("upload")) {
                var upload = details.get("upload");
                docId = upload.has("docId") ? upload.get("docId").asText() : uploadId;
                categoryId = upload.has("categoryId") ? upload.get("categoryId").asText() : null;
            }
            auditLogService.logGenerateQA("admin", docId, fineGrainCount, summaryCount, categoryId);

            return Map.of(
                    "success", true,
                    "message", "Generated Q&A pairs",
                    "result", result
            );
        } catch (Exception e) {
            log.error("Error generating more Q&A for upload: {}", uploadId, e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Document-scoped chat API endpoint.
     */
    @PostMapping("/documents/uploads/{uploadId}/chat")
    @ResponseBody
    public Map<String, Object> documentChat(
            @PathVariable String uploadId,
            @RequestBody Map<String, String> request) {
        try {
            String question = request.get("question");
            if (question == null || question.isBlank()) {
                return Map.of("success", false, "error", "Question is required");
            }
            var result = ragServiceClient.documentChat(uploadId, question);

            // Get docId for audit logging
            String docId = uploadId;
            String categoryId = null;
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details != null && details.has("upload")) {
                var upload = details.get("upload");
                docId = upload.has("docId") ? upload.get("docId").asText() : uploadId;
                categoryId = upload.has("categoryId") ? upload.get("categoryId").asText() : null;
            }
            auditLogService.logDocumentChat("admin", docId, question, categoryId);

            return Map.of(
                    "success", true,
                    "answer", result.has("answer") ? result.get("answer").asText() : ""
            );
        } catch (Exception e) {
            log.error("Error in document chat for upload: {}", uploadId, e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Add a Q&A pair to FAQ selection.
     */
    @PostMapping("/documents/uploads/{uploadId}/qa/{qaId}/select-faq")
    @ResponseBody
    public Map<String, Object> selectQAForFaq(
            @PathVariable String uploadId,
            @PathVariable Long qaId) {
        try {
            var result = ragServiceClient.addQAToFaq(uploadId, qaId);

            // Get docId for audit logging
            String docId = uploadId;
            String categoryId = null;
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details != null && details.has("upload")) {
                var upload = details.get("upload");
                docId = upload.has("docId") ? upload.get("docId").asText() : uploadId;
                categoryId = upload.has("categoryId") ? upload.get("categoryId").asText() : null;
            }
            auditLogService.logFaqSelect("admin", docId, qaId, categoryId);

            return Map.of(
                    "success", true,
                    "message", "Q&A selected for FAQ"
            );
        } catch (Exception e) {
            log.error("Error selecting Q&A {} for FAQ", qaId, e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Approve selected FAQs for a document.
     */
    @PostMapping("/documents/uploads/{uploadId}/approve-faqs")
    @ResponseBody
    public Map<String, Object> approveSelectedFaqs(
            @PathVariable String uploadId,
            @RequestParam(required = false) String approvedBy) {
        try {
            var result = ragServiceClient.approveSelectedFaqs(uploadId, approvedBy);

            // Get docId for audit logging
            String docId = uploadId;
            String categoryId = null;
            var details = ragServiceClient.getUploadDetails(uploadId);
            if (details != null && details.has("upload")) {
                var upload = details.get("upload");
                docId = upload.has("docId") ? upload.get("docId").asText() : uploadId;
                categoryId = upload.has("categoryId") ? upload.get("categoryId").asText() : null;
            }
            int faqCount = result.has("approvedCount") ? result.get("approvedCount").asInt() : 0;
            auditLogService.logFaqApprove("admin", docId, faqCount, categoryId);

            return Map.of(
                    "success", true,
                    "message", "FAQs approved",
                    "result", result
            );
        } catch (Exception e) {
            log.error("Error approving FAQs for upload: {}", uploadId, e);
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

}
