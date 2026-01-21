package com.naag.categoryadmin.repository;

import com.naag.categoryadmin.model.CategoryParameterOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CategoryParameterOverride entities.
 */
@Repository
public interface CategoryParameterOverrideRepository extends JpaRepository<CategoryParameterOverride, Long> {

    /**
     * Find all active overrides for a specific tool in a category.
     */
    List<CategoryParameterOverride> findByCategoryIdAndToolIdAndActiveTrue(String categoryId, String toolId);

    /**
     * Find all active overrides for a category.
     */
    List<CategoryParameterOverride> findByCategoryIdAndActiveTrue(String categoryId);

    /**
     * Find all active overrides for a tool (across all categories).
     */
    List<CategoryParameterOverride> findByToolIdAndActiveTrue(String toolId);

    /**
     * Find a specific override by category, tool, and parameter path.
     */
    Optional<CategoryParameterOverride> findByCategoryIdAndToolIdAndParameterPath(
            String categoryId, String toolId, String parameterPath);

    /**
     * Find tool IDs that have overrides in a category.
     */
    @Query("SELECT DISTINCT o.toolId FROM CategoryParameterOverride o WHERE o.categoryId = :categoryId AND o.active = true")
    List<String> findToolIdsWithOverrides(@Param("categoryId") String categoryId);

    /**
     * Count total overrides for a category.
     */
    @Query("SELECT COUNT(o) FROM CategoryParameterOverride o WHERE o.categoryId = :categoryId AND o.active = true")
    long countByCategoryIdActive(@Param("categoryId") String categoryId);

    /**
     * Count overrides for a specific tool in a category.
     */
    @Query("SELECT COUNT(o) FROM CategoryParameterOverride o WHERE o.categoryId = :categoryId AND o.toolId = :toolId AND o.active = true")
    long countByCategoryIdAndToolIdActive(@Param("categoryId") String categoryId, @Param("toolId") String toolId);

    /**
     * Count locked parameters for a specific tool in a category.
     */
    @Query("SELECT COUNT(o) FROM CategoryParameterOverride o WHERE o.categoryId = :categoryId AND o.toolId = :toolId AND o.lockedValue IS NOT NULL AND o.lockedValue <> '' AND o.active = true")
    long countLockedByCategoryIdAndToolId(@Param("categoryId") String categoryId, @Param("toolId") String toolId);

    /**
     * Delete all overrides for a tool in a category.
     */
    void deleteByCategoryIdAndToolId(String categoryId, String toolId);

    /**
     * Delete all overrides for a category.
     */
    void deleteByCategoryId(String categoryId);
}
