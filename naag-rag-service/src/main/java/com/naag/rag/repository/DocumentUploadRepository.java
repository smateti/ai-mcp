package com.naag.rag.repository;

import com.naag.rag.entity.DocumentUpload;
import com.naag.rag.entity.DocumentUpload.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentUploadRepository extends JpaRepository<DocumentUpload, String> {

    List<DocumentUpload> findByStatusOrderByCreatedAtDesc(ProcessingStatus status);

    List<DocumentUpload> findByStatusInOrderByCreatedAtDesc(List<ProcessingStatus> statuses);

    List<DocumentUpload> findByCategoryIdOrderByCreatedAtDesc(String categoryId);

    Optional<DocumentUpload> findByDocId(String docId);

    Optional<DocumentUpload> findByDocIdAndStatusNot(String docId, ProcessingStatus status);

    List<DocumentUpload> findAllByOrderByCreatedAtDesc();

    List<DocumentUpload> findAllByStatusNotOrderByCreatedAtDesc(ProcessingStatus status);

    List<DocumentUpload> findAllByStatus(ProcessingStatus status);
}
