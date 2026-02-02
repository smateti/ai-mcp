package com.naagi.rag.repository;

import com.naagi.rag.entity.EntityRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EntityRelationshipRepository extends JpaRepository<EntityRelationship, String> {

    List<EntityRelationship> findBySourceEntityId(String sourceEntityId);

    List<EntityRelationship> findByTargetEntityId(String targetEntityId);

    @Query("SELECT r FROM EntityRelationship r WHERE r.sourceEntityId = :entityId OR r.targetEntityId = :entityId")
    List<EntityRelationship> findByEntityId(String entityId);

    List<EntityRelationship> findByDocId(String docId);
}
