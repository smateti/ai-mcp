package com.naagi.rag.repository;

import com.naagi.rag.entity.AnswerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedback, String> {

    List<AnswerFeedback> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<AnswerFeedback> findByCategoryIdOrderByCreatedAtDesc(String categoryId);

    @Query("SELECT f.rating, COUNT(f) FROM AnswerFeedback f GROUP BY f.rating")
    List<Object[]> countByRating();

    @Query("SELECT f.categoryId, f.rating, COUNT(f) FROM AnswerFeedback f WHERE f.categoryId IS NOT NULL GROUP BY f.categoryId, f.rating")
    List<Object[]> countByRatingGroupedByCategory();

    long countByRating(int rating);

    List<AnswerFeedback> findByRatingAndCreatedAtAfter(int rating, LocalDateTime after);

    @Query("SELECT f FROM AnswerFeedback f WHERE f.rating = -1 ORDER BY f.createdAt DESC")
    List<AnswerFeedback> findNegativeFeedback();
}
