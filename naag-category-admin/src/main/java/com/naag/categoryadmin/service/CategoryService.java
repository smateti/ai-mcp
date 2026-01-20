package com.naag.categoryadmin.service;

import com.naag.categoryadmin.metrics.CategoryAdminMetrics;
import com.naag.categoryadmin.model.Category;
import com.naag.categoryadmin.repository.CategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryAdminMetrics metrics;

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
        String id = category.getId();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

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
}
