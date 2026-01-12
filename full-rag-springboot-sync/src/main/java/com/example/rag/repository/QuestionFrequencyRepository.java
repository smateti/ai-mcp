package com.example.rag.repository;

import com.example.rag.entity.QuestionFrequency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionFrequencyRepository extends JpaRepository<QuestionFrequency, Long> {

  Optional<QuestionFrequency> findByQuestionHash(String questionHash);

  // Get top N most frequently asked questions
  @Query("SELECT q FROM QuestionFrequency q ORDER BY q.askCount DESC")
  List<QuestionFrequency> findTopFrequentQuestions();

  // Get questions asked more than N times
  List<QuestionFrequency> findByAskCountGreaterThanEqual(Integer minCount);

  // Count total unique questions
  @Query("SELECT COUNT(q) FROM QuestionFrequency q")
  Long countUniqueQuestions();

  // Get total ask count across all questions
  @Query("SELECT SUM(q.askCount) FROM QuestionFrequency q")
  Long sumTotalAsks();
}
