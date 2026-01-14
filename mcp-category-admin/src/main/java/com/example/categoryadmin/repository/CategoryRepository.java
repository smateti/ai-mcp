package com.example.categoryadmin.repository;

import com.example.categoryadmin.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCategoryId(String categoryId);

    boolean existsByCategoryId(String categoryId);

    List<Category> findByActiveTrue();

    @Query("SELECT c FROM Category c WHERE c.active = true ORDER BY c.displayOrder ASC, c.name ASC")
    List<Category> findAllActiveOrderedByDisplayOrder();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.tools WHERE c.categoryId = :categoryId")
    Optional<Category> findByCategoryIdWithTools(String categoryId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.documents WHERE c.categoryId = :categoryId")
    Optional<Category> findByCategoryIdWithDocuments(String categoryId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.tools LEFT JOIN FETCH c.documents WHERE c.categoryId = :categoryId")
    Optional<Category> findByCategoryIdWithToolsAndDocuments(String categoryId);
}
