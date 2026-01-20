package com.naag.rag.repository;

import com.naag.rag.entity.GeneratedQA;
import com.naag.rag.entity.GeneratedQA.QuestionType;
import com.naag.rag.entity.GeneratedQA.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneratedQARepository extends JpaRepository<GeneratedQA, Long> {

    List<GeneratedQA> findByUploadIdOrderByIdAsc(String uploadId);

    List<GeneratedQA> findByUploadIdAndQuestionTypeOrderByIdAsc(String uploadId, QuestionType questionType);

    List<GeneratedQA> findByUploadIdAndValidationStatusOrderByIdAsc(String uploadId, ValidationStatus status);

    @Query("SELECT COUNT(q) FROM GeneratedQA q WHERE q.uploadId = :uploadId AND q.validationStatus = :status")
    long countByUploadIdAndValidationStatus(String uploadId, ValidationStatus status);

    @Query("SELECT AVG(q.similarityScore) FROM GeneratedQA q WHERE q.uploadId = :uploadId AND q.similarityScore IS NOT NULL")
    Double getAverageSimilarityScore(String uploadId);

    void deleteByUploadId(String uploadId);
}
