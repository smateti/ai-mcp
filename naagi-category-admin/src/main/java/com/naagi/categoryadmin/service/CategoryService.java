package com.naagi.categoryadmin.service;

import com.naagi.categoryadmin.client.ToolRegistryClient;
import com.naagi.categoryadmin.metrics.CategoryAdminMetrics;
import com.naagi.categoryadmin.model.Category;
import com.naagi.categoryadmin.model.Tool;
import com.naagi.categoryadmin.repository.CategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryAdminMetrics metrics;
    private final ToolRegistryClient toolRegistryClient;
    private final CategorySequenceService categorySequenceService;
    private final String ragServiceUrl;

    public CategoryService(CategoryRepository categoryRepository,
                          CategoryAdminMetrics metrics,
                          @Lazy ToolRegistryClient toolRegistryClient,
                          CategorySequenceService categorySequenceService,
                          @Value("${naagi.services.rag.url:http://localhost:8080}") String ragServiceUrl) {
        this.categoryRepository = categoryRepository;
        this.metrics = metrics;
        this.toolRegistryClient = toolRegistryClient;
        this.categorySequenceService = categorySequenceService;
        this.ragServiceUrl = ragServiceUrl;
    }

    // Constant category IDs for consistency across restarts and with vector DB
    public static final String CATEGORY_SERVICE_DEV = "service-development";
    public static final String CATEGORY_BATCH_DEV = "batch-development";
    public static final String CATEGORY_UI_DEV = "ui-development";
    public static final String CATEGORY_MISC_DEV = "misc-development";
    public static final String CATEGORY_DATA_RETRIEVAL = "data-retrieval";

    @PostConstruct
    public void initializeDefaultCategories() {
        log.info("Checking and initializing default categories...");

        createCategoryWithId(CATEGORY_SERVICE_DEV, "Service Development",
                "Spring Boot microservices, REST APIs, backend development best practices", true);

        createCategoryWithId(CATEGORY_BATCH_DEV, "Batch Development",
                "Spring Batch, scheduled jobs, ETL processes, data processing pipelines", true);

        createCategoryWithId(CATEGORY_UI_DEV, "UI Development",
                "React, Angular, Vue.js, frontend frameworks, CSS, responsive design", true);

        createCategoryWithId(CATEGORY_MISC_DEV, "Misc Development",
                "DevOps, CI/CD, Docker, Kubernetes, testing, documentation, tools", true);

        createCategoryWithId(CATEGORY_DATA_RETRIEVAL, "Data Retrieval",
                "External API calls, data fetching, third-party integrations", true);

        log.info("Default categories initialized/verified");

        // Cleanup old category-specific RAG tools (one-time migration)
        cleanupOldRagTools();
    }

    /**
     * Cleanup old category-specific RAG tools that were created before the shared RAG tool approach.
     * This removes tools like rag_query_service_development, rag_query_batch_development, etc.
     * and also removes their references from category tool lists.
     */
    private void cleanupOldRagTools() {
        List<String> oldRagToolIds = List.of(
                "rag_query_service_development",
                "rag_query_batch_development",
                "rag_query_ui_development",
                "rag_query_misc_development",
                "rag_query_data_retrieval"
        );

        for (String oldToolId : oldRagToolIds) {
            try {
                // Delete from tool registry
                toolRegistryClient.deleteTool(oldToolId);
                log.info("Deleted old RAG tool: {}", oldToolId);
            } catch (Exception e) {
                // Tool might not exist, ignore
                log.debug("Could not delete old RAG tool {} (may not exist): {}", oldToolId, e.getMessage());
            }
        }

        // Also clean up category tool lists to remove references to old RAG tools
        for (Category category : categoryRepository.findAll()) {
            if (category.getToolIds() != null) {
                boolean modified = false;
                List<String> cleanedToolIds = new ArrayList<>(category.getToolIds());
                for (String oldToolId : oldRagToolIds) {
                    if (cleanedToolIds.remove(oldToolId)) {
                        modified = true;
                        log.info("Removed old RAG tool {} from category {}", oldToolId, category.getId());
                    }
                }
                if (modified) {
                    category.setToolIds(cleanedToolIds);
                    categoryRepository.save(category);
                }
            }
        }
    }

    private void createCategoryWithId(String id, String name, String description, boolean active) {
        if (!categoryRepository.existsById(id)) {
            Category category = Category.builder()
                    .id(id)
                    .name(name)
                    .description(description)
                    .active(active)
                    .build();
            categoryRepository.save(category);
            log.info("Created default category: {} ({})", name, id);
        }
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Optional<Category> getCategory(String id) {
        return categoryRepository.findById(id);
    }

    @Transactional
    public Category createCategory(Category category) {
        // Generate sequential ID (ignore any ID passed in)
        String id = categorySequenceService.generateCategoryId();

        Category newCategory = Category.builder()
                .id(id)
                .name(category.getName())
                .description(category.getDescription())
                .toolIds(category.getToolIds() != null ? new ArrayList<>(category.getToolIds()) : new ArrayList<>())
                .active(category.isActive())
                .build();

        Category saved = categoryRepository.save(newCategory);
        metrics.recordCategoryCreated();
        updateCategoryCount();
        log.info("Created category: {} ({})", saved.getName(), saved.getId());
        return saved;
    }

    @Transactional
    public Category updateCategory(String id, Category category) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

        existing.setName(category.getName() != null ? category.getName() : existing.getName());
        existing.setDescription(category.getDescription() != null ? category.getDescription() : existing.getDescription());
        existing.setActive(category.isActive());

        if (category.getToolIds() != null) {
            existing.setToolIds(new ArrayList<>(category.getToolIds()));
        }

        Category saved = categoryRepository.save(existing);
        metrics.recordCategoryUpdated();
        log.info("Updated category: {} ({})", saved.getName(), id);
        return saved;
    }

    @Transactional
    public void deleteCategory(String id) {
        categoryRepository.findById(id).ifPresent(category -> {
            categoryRepository.delete(category);
            metrics.recordCategoryDeleted();
            updateCategoryCount();
            log.info("Deleted category: {} ({})", category.getName(), id);
        });
    }

    @Transactional
    public void addToolToCategory(String categoryId, String toolId) {
        categoryRepository.findById(categoryId).ifPresent(category -> {
            if (category.getToolIds() == null) {
                category.setToolIds(new ArrayList<>());
            }
            if (!category.getToolIds().contains(toolId)) {
                category.getToolIds().add(toolId);
                categoryRepository.save(category);
                log.info("Added tool {} to category {}", toolId, categoryId);
            }
        });
    }

    @Transactional
    public void removeToolFromCategory(String categoryId, String toolId) {
        categoryRepository.findById(categoryId).ifPresent(category -> {
            if (category.getToolIds() != null) {
                category.getToolIds().remove(toolId);
                categoryRepository.save(category);
                log.info("Removed tool {} from category {}", toolId, categoryId);
            }
        });
    }

    public List<Category> getActiveCategories() {
        return categoryRepository.findByActiveTrue();
    }

    @Transactional
    public void resetToolPriorities(String categoryId) {
        categoryRepository.findById(categoryId).ifPresent(category -> {
            if (category.getToolIds() != null && !category.getToolIds().isEmpty()) {
                List<String> sorted = new ArrayList<>(category.getToolIds());
                Collections.sort(sorted);
                category.setToolIds(sorted);
                categoryRepository.save(category);
                log.info("Reset tool priorities for category {}", categoryId);
            }
        });
    }

    @Transactional
    public void reorderTools(String categoryId, List<String> newOrder) {
        categoryRepository.findById(categoryId).ifPresent(category -> {
            category.setToolIds(new ArrayList<>(newOrder));
            categoryRepository.save(category);
            log.info("Reordered tools for category {}", categoryId);
        });
    }

    private void updateCategoryCount() {
        metrics.setTotalCategoryCount((int) categoryRepository.count());
    }

    private static final String RAG_TOOL_ID = "rag_query";

    /**
     * Ensures the shared RAG query tool exists and is added to the category.
     * Called when the first document is added to a category.
     * The tool is shared across all categories - categoryId is passed at runtime.
     *
     * @param categoryId The category ID to add the tool to
     * @return true if tool was added to category, false if it already had it
     */
    @Transactional
    public boolean ensureRagToolForCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return false;
        }

        Optional<Category> categoryOpt = categoryRepository.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            log.warn("Category not found: {}", categoryId);
            return false;
        }

        Category category = categoryOpt.get();

        // Check if category already has the RAG tool
        if (category.getToolIds() != null && category.getToolIds().contains(RAG_TOOL_ID)) {
            log.debug("Category {} already has RAG tool", categoryId);
            return false;
        }

        // Check if the shared RAG tool exists in registry, create if not
        Optional<Tool> existingTool = toolRegistryClient.getTool(RAG_TOOL_ID);
        if (existingTool.isEmpty()) {
            log.info("Creating shared RAG query tool");
            try {
                Tool ragTool = Tool.builder()
                        .id(RAG_TOOL_ID)
                        .name("RAG Query")
                        .description("Search the knowledge base to answer questions about concepts, best practices, how-to guides, and general knowledge. " +
                                "Use this for questions like: 'What is...', 'How to...', 'Explain...', 'Best practices for...'")
                        .serviceUrl(ragServiceUrl)
                        .method("POST")
                        .path("/api/rag/query")
                        .active(true)
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "question", Map.of("type", "string", "description", "The question to search for in the knowledge base"),
                                        "categoryId", Map.of("type", "string", "description", "Category to search in (filters results to specific domain)"),
                                        "topK", Map.of("type", "integer", "description", "Number of results to return", "default", 5)
                                ),
                                "required", List.of("question")
                        ))
                        .build();

                toolRegistryClient.createTool(ragTool);
                log.info("Created shared RAG query tool: {}", RAG_TOOL_ID);
            } catch (Exception e) {
                log.error("Failed to create RAG tool: {}", e.getMessage());
                return false;
            }
        }

        // Add tool to category
        addToolToCategory(categoryId, RAG_TOOL_ID);
        log.info("Added RAG tool to category {}", categoryId);
        return true;
    }
}
