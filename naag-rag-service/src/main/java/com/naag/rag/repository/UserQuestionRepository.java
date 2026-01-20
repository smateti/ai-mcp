package com.naag.rag.repository;

import com.naag.rag.entity.UserQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for UserQuestion - focused on FAQ analytics, not audit.
 * This tracks unique questions with frequency for FAQ candidate identification.
 */
@Repository
public interface UserQuestionRepository extends JpaRepository<UserQuestion, String> {

    // Find by Qdrant point ID (for deduplication lookup)
    Optional<UserQuestion> findByQdrantPointId(String qdrantPointId);

    // Find questions by category
    Page<UserQuestion> findByCategoryIdOrderByFrequencyDesc(String categoryId, Pageable pageable);

    // Find questions with matched FAQ
    Page<UserQuestion> findByMatchedFaqIdIsNotNullOrderByFrequencyDesc(Pageable pageable);

    // Find questions without matched FAQ (potential new FAQs)
    Page<UserQuestion> findByMatchedFaqIdIsNullOrderByFrequencyDesc(Pageable pageable);

    // Find questions without matched FAQ by category
    Page<UserQuestion> findByCategoryIdAndMatchedFaqIdIsNullOrderByFrequencyDesc(
            String categoryId, Pageable pageable);

    // Find frequently asked questions (min frequency threshold)
    @Query("SELECT u FROM UserQuestion u WHERE u.frequency >= :minFrequency ORDER BY u.frequency DESC")
    Page<UserQuestion> findFrequentQuestions(@Param("minFrequency") int minFrequency, Pageable pageable);

    // Find frequently asked questions by category
    @Query("SELECT u FROM UserQuestion u WHERE u.categoryId = :categoryId AND u.frequency >= :minFrequency ORDER BY u.frequency DESC")
    Page<UserQuestion> findFrequentQuestionsByCategory(
            @Param("categoryId") String categoryId,
            @Param("minFrequency") int minFrequency,
            Pageable pageable);

    // Find frequently asked questions without FAQ match (best candidates for new FAQs)
    @Query("SELECT u FROM UserQuestion u WHERE u.frequency >= :minFrequency AND u.matchedFaqId IS NULL ORDER BY u.frequency DESC")
    Page<UserQuestion> findFrequentUnmatchedQuestions(
            @Param("minFrequency") int minFrequency, Pageable pageable);

    // Find by category and FAQ match status
    @Query("SELECT u FROM UserQuestion u WHERE " +
           "(:categoryId IS NULL OR u.categoryId = :categoryId) AND " +
           "(:hasMatchedFaq IS NULL OR " +
           " (:hasMatchedFaq = true AND u.matchedFaqId IS NOT NULL) OR " +
           " (:hasMatchedFaq = false AND u.matchedFaqId IS NULL)) " +
           "ORDER BY u.frequency DESC")
    Page<UserQuestion> findByCategoryAndFaqStatus(
            @Param("categoryId") String categoryId,
            @Param("hasMatchedFaq") Boolean hasMatchedFaq,
            Pageable pageable);

    // Count questions by category
    long countByCategoryId(String categoryId);

    // Count questions with FAQ match
    long countByMatchedFaqIdIsNotNull();

    // Count questions without FAQ match
    long countByMatchedFaqIdIsNull();

    // Get total question frequency (sum of all frequencies = total times questions asked)
    @Query("SELECT COALESCE(SUM(u.frequency), 0) FROM UserQuestion u")
    long getTotalQuestionCount();

    // Get total question frequency by category
    @Query("SELECT COALESCE(SUM(u.frequency), 0) FROM UserQuestion u WHERE u.categoryId = :categoryId")
    long getTotalQuestionCountByCategory(@Param("categoryId") String categoryId);

    // Increment frequency for a question
    @Modifying
    @Query("UPDATE UserQuestion u SET u.frequency = u.frequency + 1, u.lastAskedAt = :now WHERE u.id = :id")
    void incrementFrequency(@Param("id") String id, @Param("now") LocalDateTime now);

    // Find questions not promoted to FAQ (still in analytics tracking)
    Page<UserQuestion> findByPromotedToFaqFalseOrderByFrequencyDesc(Pageable pageable);

    // Search questions by text (using native query due to CLOB type)
    @Query(value = "SELECT * FROM user_questions u WHERE LOWER(CAST(u.question AS VARCHAR(10000))) LIKE LOWER(CONCAT('%', :searchText, '%')) ORDER BY u.frequency DESC",
           countQuery = "SELECT COUNT(*) FROM user_questions u WHERE LOWER(CAST(u.question AS VARCHAR(10000))) LIKE LOWER(CONCAT('%', :searchText, '%'))",
           nativeQuery = true)
    Page<UserQuestion> searchByQuestionText(@Param("searchText") String searchText, Pageable pageable);

    // Complex filter query (legacy without time range)
    @Query("SELECT u FROM UserQuestion u WHERE " +
           "(:categoryId IS NULL OR u.categoryId = :categoryId) AND " +
           "(:hasMatchedFaq IS NULL OR " +
           " (:hasMatchedFaq = true AND u.matchedFaqId IS NOT NULL) OR " +
           " (:hasMatchedFaq = false AND u.matchedFaqId IS NULL)) AND " +
           "(:minFrequency IS NULL OR u.frequency >= :minFrequency)")
    Page<UserQuestion> findWithFilters(
            @Param("categoryId") String categoryId,
            @Param("hasMatchedFaq") Boolean hasMatchedFaq,
            @Param("minFrequency") Integer minFrequency,
            Pageable pageable);

    // Complex filter query with time range support
    @Query("SELECT u FROM UserQuestion u WHERE " +
           "(:categoryId IS NULL OR u.categoryId = :categoryId) AND " +
           "(:hasMatchedFaq IS NULL OR " +
           " (:hasMatchedFaq = true AND u.matchedFaqId IS NOT NULL) OR " +
           " (:hasMatchedFaq = false AND u.matchedFaqId IS NULL)) AND " +
           "(:minFrequency IS NULL OR u.frequency >= :minFrequency) AND " +
           "(:fromDate IS NULL OR u.lastAskedAt >= :fromDate) AND " +
           "(:toDate IS NULL OR u.lastAskedAt <= :toDate)")
    Page<UserQuestion> findWithFilters(
            @Param("categoryId") String categoryId,
            @Param("hasMatchedFaq") Boolean hasMatchedFaq,
            @Param("minFrequency") Integer minFrequency,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);
}
