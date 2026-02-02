package com.naagi.rag.repository;

import com.naagi.rag.entity.ExtractedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExtractedEntityRepository extends JpaRepository<ExtractedEntity, String> {

    List<ExtractedEntity> findByDocId(String docId);

    List<ExtractedEntity> findByCategoryId(String categoryId);

    Optional<ExtractedEntity> findByNameIgnoreCaseAndDocId(String name, String docId);

    @Query("SELECT e FROM ExtractedEntity e WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<ExtractedEntity> searchByName(String term);

    @Query("SELECT e FROM ExtractedEntity e WHERE e.categoryId = :categoryId AND LOWER(e.name) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<ExtractedEntity> searchByNameAndCategory(String term, String categoryId);

    List<ExtractedEntity> findByEntityType(String entityType);
}
