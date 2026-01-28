package com.naagi.chat.repository;

import com.naagi.chat.entity.AuditLogDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogElasticsearchRepository extends ElasticsearchRepository<AuditLogDocument, String> {

    List<AuditLogDocument> findByUserIdOrderByTimestampDesc(String userId);

    Page<AuditLogDocument> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    List<AuditLogDocument> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<AuditLogDocument> findByUserIdAndAction(String userId, String action);

    List<AuditLogDocument> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
            String userId, LocalDateTime start, LocalDateTime end);

    List<AuditLogDocument> findByUserIdAndSuccessFalseOrderByTimestampDesc(String userId);

    @Query("{\"bool\": {\"must\": [{\"match\": {\"userId\": \"?0\"}}, {\"multi_match\": {\"query\": \"?1\", \"fields\": [\"userQuestion\", \"assistantResponse\"]}}]}}")
    List<AuditLogDocument> searchByContent(String userId, String query);

    long countByUserIdAndAction(String userId, String action);
}
