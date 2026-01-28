package com.naagi.categoryadmin.controller;

import com.naagi.categoryadmin.model.Category;
import com.naagi.categoryadmin.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CategoryApiController {

    private final CategoryService categoryService;

    @GetMapping
    public List<Category> getAllCategories() {
        return categoryService.getAllCategories();
    }

    @GetMapping("/active")
    public List<Category> getActiveCategories() {
        return categoryService.getActiveCategories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategory(@PathVariable String id) {
        return categoryService.getCategory(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Category> createCategory(@RequestBody Category category) {
        Category created = categoryService.createCategory(category);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable String id, @RequestBody Category category) {
        try {
            Category updated = categoryService.updateCategory(id, category);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{categoryId}/tools/{toolId}")
    public ResponseEntity<Void> addToolToCategory(@PathVariable String categoryId, @PathVariable String toolId) {
        categoryService.addToolToCategory(categoryId, toolId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{categoryId}/tools/{toolId}")
    public ResponseEntity<Void> removeToolFromCategory(@PathVariable String categoryId, @PathVariable String toolId) {
        categoryService.removeToolFromCategory(categoryId, toolId);
        return ResponseEntity.ok().build();
    }
}
