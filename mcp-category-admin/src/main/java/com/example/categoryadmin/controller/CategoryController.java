package com.example.categoryadmin.controller;

import com.example.categoryadmin.dto.*;
import com.example.categoryadmin.service.CategoryService;
import com.example.categoryadmin.service.McpClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Category Management", description = "APIs for managing MCP tool and document categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;
    private final McpClientService mcpClientService;

    @PostMapping
    @Operation(summary = "Create a new category")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(categoryService.createCategory(request));
    }

    @GetMapping
    @Operation(summary = "Get all categories")
    public ResponseEntity<List<CategoryDto>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/active")
    @Operation(summary = "Get active categories ordered by display order")
    public ResponseEntity<List<CategoryDto>> getActiveCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Get category by ID with tools and documents")
    public ResponseEntity<CategoryDto> getCategoryById(@PathVariable String categoryId) {
        return ResponseEntity.ok(categoryService.getCategoryById(categoryId));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Update category")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable String categoryId,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(categoryId, request));
    }

    @PostMapping("/{categoryId}/toggle-active")
    @Operation(summary = "Toggle category active status")
    public ResponseEntity<CategoryDto> toggleCategoryActive(@PathVariable String categoryId) {
        return ResponseEntity.ok(categoryService.toggleCategoryActive(categoryId));
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Delete category")
    public ResponseEntity<Void> deleteCategory(@PathVariable String categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    // Tool Management

    @GetMapping("/available-tools")
    @Operation(summary = "Get available tools from MCP server")
    public ResponseEntity<List<McpClientService.ToolInfo>> getAvailableTools() {
        return ResponseEntity.ok(mcpClientService.listTools());
    }

    @PostMapping("/{categoryId}/tools")
    @Operation(summary = "Add tool to category")
    public ResponseEntity<CategoryToolDto> addToolToCategory(
            @PathVariable String categoryId,
            @Valid @RequestBody AddToolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(categoryService.addToolToCategory(categoryId, request));
    }

    @GetMapping("/{categoryId}/tools")
    @Operation(summary = "Get tools for category")
    public ResponseEntity<List<CategoryToolDto>> getToolsForCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(categoryService.getToolsForCategory(categoryId));
    }

    @GetMapping("/{categoryId}/tools/enabled")
    @Operation(summary = "Get enabled tool IDs for category")
    public ResponseEntity<Map<String, List<String>>> getEnabledToolIds(@PathVariable String categoryId) {
        return ResponseEntity.ok(Map.of("toolIds", categoryService.getEnabledToolIds(categoryId)));
    }

    @DeleteMapping("/{categoryId}/tools/{toolId}")
    @Operation(summary = "Remove tool from category")
    public ResponseEntity<Void> removeToolFromCategory(
            @PathVariable String categoryId,
            @PathVariable String toolId) {
        categoryService.removeToolFromCategory(categoryId, toolId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{categoryId}/tools/{toolId}/toggle-enabled")
    @Operation(summary = "Toggle tool enabled status")
    public ResponseEntity<CategoryToolDto> toggleToolEnabled(
            @PathVariable String categoryId,
            @PathVariable String toolId) {
        return ResponseEntity.ok(categoryService.toggleToolEnabled(categoryId, toolId));
    }

    @PutMapping("/{categoryId}/tools/{toolId}/priority")
    @Operation(summary = "Update tool priority")
    public ResponseEntity<CategoryToolDto> updateToolPriority(
            @PathVariable String categoryId,
            @PathVariable String toolId,
            @RequestBody Map<String, Integer> body) {
        Integer priority = body.get("priority");
        if (priority == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(categoryService.updateToolPriority(categoryId, toolId, priority));
    }

    // Document Management

    @PostMapping("/{categoryId}/documents")
    @Operation(summary = "Add document to category")
    public ResponseEntity<CategoryDocumentDto> addDocumentToCategory(
            @PathVariable String categoryId,
            @Valid @RequestBody AddDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(categoryService.addDocumentToCategory(categoryId, request));
    }

    @GetMapping("/{categoryId}/documents")
    @Operation(summary = "Get documents for category")
    public ResponseEntity<List<CategoryDocumentDto>> getDocumentsForCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(categoryService.getDocumentsForCategory(categoryId));
    }

    @GetMapping("/{categoryId}/documents/enabled")
    @Operation(summary = "Get enabled document IDs for category")
    public ResponseEntity<Map<String, List<String>>> getEnabledDocumentIds(@PathVariable String categoryId) {
        return ResponseEntity.ok(Map.of("documentIds", categoryService.getEnabledDocumentIds(categoryId)));
    }

    @DeleteMapping("/{categoryId}/documents/{documentId}")
    @Operation(summary = "Remove document from category")
    public ResponseEntity<Void> removeDocumentFromCategory(
            @PathVariable String categoryId,
            @PathVariable String documentId) {
        categoryService.removeDocumentFromCategory(categoryId, documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{categoryId}/documents/{documentId}/toggle-enabled")
    @Operation(summary = "Toggle document enabled status")
    public ResponseEntity<CategoryDocumentDto> toggleDocumentEnabled(
            @PathVariable String categoryId,
            @PathVariable String documentId) {
        return ResponseEntity.ok(categoryService.toggleDocumentEnabled(categoryId, documentId));
    }
}
