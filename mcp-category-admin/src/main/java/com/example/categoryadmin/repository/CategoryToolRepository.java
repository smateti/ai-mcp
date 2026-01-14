package com.example.categoryadmin.repository;

import com.example.categoryadmin.entity.CategoryTool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryToolRepository extends JpaRepository<CategoryTool, Long> {

    @Query("SELECT ct FROM CategoryTool ct WHERE ct.category.categoryId = :categoryId")
    List<CategoryTool> findByCategoryId(String categoryId);

    @Query("SELECT ct FROM CategoryTool ct WHERE ct.category.categoryId = :categoryId AND ct.enabled = true ORDER BY ct.priority DESC")
    List<CategoryTool> findEnabledByCategoryIdOrderedByPriority(String categoryId);

    @Query("SELECT ct FROM CategoryTool ct WHERE ct.category.categoryId = :categoryId AND ct.toolId = :toolId")
    Optional<CategoryTool> findByCategoryIdAndToolId(String categoryId, String toolId);

    @Query("SELECT DISTINCT ct.toolId FROM CategoryTool ct WHERE ct.category.categoryId = :categoryId AND ct.enabled = true")
    List<String> findToolIdsByCategoryId(String categoryId);

    @Query("SELECT CASE WHEN COUNT(ct) > 0 THEN true ELSE false END FROM CategoryTool ct WHERE ct.category.categoryId = :categoryId AND ct.toolId = :toolId")
    boolean existsByCategoryIdAndToolId(String categoryId, String toolId);

    @Modifying
    @Query("DELETE FROM CategoryTool ct WHERE ct.category.categoryId = :categoryId AND ct.toolId = :toolId")
    void deleteByCategoryIdAndToolId(String categoryId, String toolId);
}
