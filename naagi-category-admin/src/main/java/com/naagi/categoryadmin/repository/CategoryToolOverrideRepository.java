package com.naagi.categoryadmin.repository;

import com.naagi.categoryadmin.model.CategoryToolOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for category-level tool overrides.
 */
@Repository
public interface CategoryToolOverrideRepository extends JpaRepository<CategoryToolOverride, Long> {

    /**
     * Find active tool override for a specific category and tool.
     */
    Optional<CategoryToolOverride> findByCategoryIdAndToolIdAndActiveTrue(String categoryId, String toolId);

    /**
     * Find tool override by category and tool (regardless of active status).
     */
    Optional<CategoryToolOverride> findByCategoryIdAndToolId(String categoryId, String toolId);

    /**
     * Find all active tool overrides for a category.
     */
    List<CategoryToolOverride> findByCategoryIdAndActiveTrue(String categoryId);

    /**
     * Find all tool overrides for a category.
     */
    List<CategoryToolOverride> findByCategoryId(String categoryId);

    /**
     * Delete all overrides for a category.
     */
    void deleteByCategoryId(String categoryId);

    /**
     * Delete override for a specific tool in a category.
     */
    void deleteByCategoryIdAndToolId(String categoryId, String toolId);
}
