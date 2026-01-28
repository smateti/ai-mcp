package com.naagi.rag.repository;

import com.naagi.rag.entity.FaqEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaqEntryRepository extends JpaRepository<FaqEntry, Long> {

    // Paginated queries
    Page<FaqEntry> findByActiveTrue(Pageable pageable);

    Page<FaqEntry> findByCategoryIdAndActiveTrue(String categoryId, Pageable pageable);

    @Query("SELECT f FROM FaqEntry f WHERE f.active = true AND (LOWER(f.question) LIKE LOWER(CONCAT('%', :searchText, '%')) OR LOWER(f.answer) LIKE LOWER(CONCAT('%', :searchText, '%')))")
    Page<FaqEntry> searchByQuestionOrAnswer(@Param("searchText") String searchText, Pageable pageable);

    // Count queries
    long countByActiveTrue();

    @Query("SELECT COALESCE(SUM(f.accessCount), 0) FROM FaqEntry f WHERE f.active = true")
    long sumAccessCount();

    List<FaqEntry> findByCategoryIdAndActiveTrueOrderByAccessCountDesc(String categoryId);

    List<FaqEntry> findByDocIdAndActiveTrue(String docId);

    List<FaqEntry> findByUploadIdAndActiveTrue(String uploadId);

    @Query("SELECT f FROM FaqEntry f WHERE f.categoryId = :categoryId AND f.active = true AND LOWER(f.question) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<FaqEntry> searchByQuestion(@Param("categoryId") String categoryId, @Param("query") String query);

    @Query("SELECT f FROM FaqEntry f WHERE f.categoryId = :categoryId AND f.active = true ORDER BY f.accessCount DESC")
    List<FaqEntry> findTopFaqsByCategory(@Param("categoryId") String categoryId);

    @Query("SELECT COUNT(f) FROM FaqEntry f WHERE f.categoryId = :categoryId AND f.active = true")
    long countByCategoryId(@Param("categoryId") String categoryId);

    @Modifying
    @Query("UPDATE FaqEntry f SET f.active = false WHERE f.docId = :docId")
    void deactivateByDocId(@Param("docId") String docId);

    @Modifying
    @Query("UPDATE FaqEntry f SET f.active = false WHERE f.uploadId = :uploadId")
    void deactivateByUploadId(@Param("uploadId") String uploadId);

    @Modifying
    @Query("UPDATE FaqEntry f SET f.active = false WHERE f.categoryId = :categoryId")
    void deactivateByCategoryId(@Param("categoryId") String categoryId);

    Optional<FaqEntry> findByCategoryIdAndQuestionAndActiveTrue(String categoryId, String question);

    @Query("SELECT DISTINCT f.categoryId FROM FaqEntry f WHERE f.active = true")
    List<String> findDistinctCategoryIds();
}
