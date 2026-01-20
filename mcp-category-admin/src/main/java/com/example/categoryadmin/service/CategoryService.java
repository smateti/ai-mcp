package com.example.categoryadmin.service;

import com.example.categoryadmin.dto.*;
import com.example.categoryadmin.entity.Category;
import com.example.categoryadmin.entity.CategoryDocument;
import com.example.categoryadmin.entity.CategoryTool;
import com.example.categoryadmin.repository.CategoryDocumentRepository;
import com.example.categoryadmin.repository.CategoryRepository;
import com.example.categoryadmin.repository.CategoryToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryToolRepository categoryToolRepository;
    private final CategoryDocumentRepository categoryDocumentRepository;

    /**
     * Create a new category
     */
    @Transactional
    public CategoryDto createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByCategoryId(request.getCategoryId())) {
            throw new IllegalArgumentException("Category with ID '" + request.getCategoryId() + "' already exists");
        }

        Category category = new Category(
            request.getCategoryId(),
            request.getName(),
            request.getDescription()
        );
        category.setDisplayOrder(request.getDisplayOrder());

        category = categoryRepository.save(category);
        log.info("Created category: {}", category.getCategoryId());

        return toCategoryDto(category);
    }

    /**
     * Get all categories
     */
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream()
            .map(this::toCategoryDto)
            .collect(Collectors.toList());
    }

    /**
     * Get active categories ordered by display order
     */
    @Transactional(readOnly = true)
    public List<CategoryDto> getActiveCategories() {
        return categoryRepository.findAllActiveOrderedByDisplayOrder().stream()
            .map(this::toCategoryDto)
            .collect(Collectors.toList());
    }

    /**
     * Get category by ID with tools and documents
     */
    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(String categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        CategoryDto dto = toCategoryDto(category);
        dto.setTools(category.getTools().stream()
            .map(this::toToolDto)
            .collect(Collectors.toList()));
        dto.setDocuments(category.getDocuments().stream()
            .map(this::toDocumentDto)
            .collect(Collectors.toList()));

        return dto;
    }

    /**
     * Update category
     */
    @Transactional
    public CategoryDto updateCategory(String categoryId, CreateCategoryRequest request) {
        Category category = categoryRepository.findByCategoryId(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setDisplayOrder(request.getDisplayOrder());

        category = categoryRepository.save(category);
        log.info("Updated category: {}", categoryId);

        return toCategoryDto(category);
    }

    /**
     * Toggle category active status
     */
    @Transactional
    public CategoryDto toggleCategoryActive(String categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        category.setActive(!category.getActive());
        category = categoryRepository.save(category);
        log.info("Toggled category {} active status to: {}", categoryId, category.getActive());

        return toCategoryDto(category);
    }

    /**
     * Delete category
     */
    @Transactional
    public void deleteCategory(String categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        categoryRepository.delete(category);
        log.info("Deleted category: {}", categoryId);
    }

    /**
     * Add tool to category
     */
    @Transactional
    public CategoryToolDto addToolToCategory(String categoryId, AddToolRequest request) {
        Category category = categoryRepository.findByCategoryId(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        if (categoryToolRepository.existsByCategoryIdAndToolId(categoryId, request.getToolId())) {
            throw new IllegalArgumentException("Tool '" + request.getToolId() + "' already exists in category");
        }

        CategoryTool tool = new CategoryTool(
            category,
            request.getToolId(),
            request.getToolName(),
            request.getToolDescription()
        );
        tool.setPriority(request.getPriority());

        tool = categoryToolRepository.save(tool);
        log.info("Added tool {} to category {}", request.getToolId(), categoryId);

        return toToolDto(tool);
    }

    /**
     * Remove tool from category
     */
    @Transactional
    public void removeToolFromCategory(String categoryId, String toolId) {
        CategoryTool tool = categoryToolRepository.findByCategoryIdAndToolId(categoryId, toolId)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found in category"));

        categoryToolRepository.delete(tool);
        log.info("Removed tool {} from category {}", toolId, categoryId);
    }

    /**
     * Get tools for category
     */
    @Transactional(readOnly = true)
    public List<CategoryToolDto> getToolsForCategory(String categoryId) {
        return categoryToolRepository.findByCategoryId(categoryId).stream()
            .map(this::toToolDto)
            .collect(Collectors.toList());
    }

    /**
     * Get enabled tool IDs for category
     */
    @Transactional(readOnly = true)
    public List<String> getEnabledToolIds(String categoryId) {
        return categoryToolRepository.findToolIdsByCategoryId(categoryId);
    }

    /**
     * Add document to category
     */
    @Transactional
    public CategoryDocumentDto addDocumentToCategory(String categoryId, AddDocumentRequest request) {
        Category category = categoryRepository.findByCategoryId(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        if (categoryDocumentRepository.existsByCategoryIdAndDocumentId(categoryId, request.getDocumentId())) {
            throw new IllegalArgumentException("Document '" + request.getDocumentId() + "' already exists in category");
        }

        CategoryDocument document = new CategoryDocument(
            category,
            request.getDocumentId(),
            request.getDocumentName(),
            request.getDocumentDescription()
        );
        document.setDocumentType(request.getDocumentType());

        document = categoryDocumentRepository.save(document);
        log.info("Added document {} to category {}", request.getDocumentId(), categoryId);

        return toDocumentDto(document);
    }

    /**
     * Remove document from category
     */
    @Transactional
    public void removeDocumentFromCategory(String categoryId, String documentId) {
        CategoryDocument document = categoryDocumentRepository.findByCategoryIdAndDocumentId(categoryId, documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found in category"));

        categoryDocumentRepository.delete(document);
        log.info("Removed document {} from category {}", documentId, categoryId);
    }

    /**
     * Get documents for category
     */
    @Transactional(readOnly = true)
    public List<CategoryDocumentDto> getDocumentsForCategory(String categoryId) {
        return categoryDocumentRepository.findByCategoryId(categoryId).stream()
            .map(this::toDocumentDto)
            .collect(Collectors.toList());
    }

    /**
     * Get enabled document IDs for category
     */
    @Transactional(readOnly = true)
    public List<String> getEnabledDocumentIds(String categoryId) {
        return categoryDocumentRepository.findDocumentIdsByCategoryId(categoryId);
    }

    /**
     * Toggle tool enabled status
     */
    @Transactional
    public CategoryToolDto toggleToolEnabled(String categoryId, String toolId) {
        CategoryTool tool = categoryToolRepository.findByCategoryIdAndToolId(categoryId, toolId)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found in category"));

        tool.setEnabled(!tool.getEnabled());
        tool = categoryToolRepository.save(tool);
        log.info("Toggled tool {} enabled status to: {}", toolId, tool.getEnabled());

        return toToolDto(tool);
    }

    /**
     * Update tool priority
     */
    @Transactional
    public CategoryToolDto updateToolPriority(String categoryId, String toolId, Integer priority) {
        CategoryTool tool = categoryToolRepository.findByCategoryIdAndToolId(categoryId, toolId)
            .orElseThrow(() -> new IllegalArgumentException("Tool not found in category"));

        tool.setPriority(priority);
        tool = categoryToolRepository.save(tool);
        log.info("Updated tool {} priority to: {}", toolId, priority);

        return toToolDto(tool);
    }

    /**
     * Toggle document enabled status
     */
    @Transactional
    public CategoryDocumentDto toggleDocumentEnabled(String categoryId, String documentId) {
        CategoryDocument document = categoryDocumentRepository.findByCategoryIdAndDocumentId(categoryId, documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found in category"));

        document.setEnabled(!document.getEnabled());
        document = categoryDocumentRepository.save(document);
        log.info("Toggled document {} enabled status to: {}", documentId, document.getEnabled());

        return toDocumentDto(document);
    }

    // DTO Converters

    private CategoryDto toCategoryDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setCategoryId(category.getCategoryId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setActive(category.getActive());
        dto.setDisplayOrder(category.getDisplayOrder());
        dto.setToolCount(category.getTools().size());
        dto.setDocumentCount(category.getDocuments().size());
        dto.setCreatedAt(category.getCreatedAt());
        dto.setUpdatedAt(category.getUpdatedAt());
        return dto;
    }

    private CategoryToolDto toToolDto(CategoryTool tool) {
        CategoryToolDto dto = new CategoryToolDto();
        dto.setId(tool.getId());
        dto.setToolId(tool.getToolId());
        dto.setToolName(tool.getToolName());
        dto.setToolDescription(tool.getToolDescription());
        dto.setEnabled(tool.getEnabled());
        dto.setPriority(tool.getPriority());
        dto.setAddedAt(tool.getAddedAt());
        return dto;
    }

    private CategoryDocumentDto toDocumentDto(CategoryDocument document) {
        CategoryDocumentDto dto = new CategoryDocumentDto();
        dto.setId(document.getId());
        dto.setDocumentId(document.getDocumentId());
        dto.setDocumentName(document.getDocumentName());
        dto.setDocumentDescription(document.getDocumentDescription());
        dto.setDocumentType(document.getDocumentType());
        dto.setEnabled(document.getEnabled());
        dto.setAddedAt(document.getAddedAt());
        return dto;
    }
}
