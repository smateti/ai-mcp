package com.example.categoryadmin.repository;

import com.example.categoryadmin.entity.CategoryDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryDocumentRepository extends JpaRepository<CategoryDocument, Long> {

    @Query("SELECT cd FROM CategoryDocument cd WHERE cd.category.categoryId = :categoryId")
    List<CategoryDocument> findByCategoryId(String categoryId);

    @Query("SELECT cd FROM CategoryDocument cd WHERE cd.category.categoryId = :categoryId AND cd.enabled = true")
    List<CategoryDocument> findEnabledByCategoryId(String categoryId);

    @Query("SELECT cd FROM CategoryDocument cd WHERE cd.category.categoryId = :categoryId AND cd.documentId = :documentId")
    Optional<CategoryDocument> findByCategoryIdAndDocumentId(String categoryId, String documentId);

    @Query("SELECT DISTINCT cd.documentId FROM CategoryDocument cd WHERE cd.category.categoryId = :categoryId AND cd.enabled = true")
    List<String> findDocumentIdsByCategoryId(String categoryId);

    @Query("SELECT CASE WHEN COUNT(cd) > 0 THEN true ELSE false END FROM CategoryDocument cd WHERE cd.category.categoryId = :categoryId AND cd.documentId = :documentId")
    boolean existsByCategoryIdAndDocumentId(String categoryId, String documentId);

    @Modifying
    @Query("DELETE FROM CategoryDocument cd WHERE cd.category.categoryId = :categoryId AND cd.documentId = :documentId")
    void deleteByCategoryIdAndDocumentId(String categoryId, String documentId);
}
