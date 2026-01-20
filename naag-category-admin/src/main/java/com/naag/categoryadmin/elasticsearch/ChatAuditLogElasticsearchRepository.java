package com.naag.categoryadmin.elasticsearch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch repository for chat audit logs.
 */
@Repository
@ConditionalOnProperty(name = "naag.elasticsearch.enabled", havingValue = "true")
public interface ChatAuditLogElasticsearchRepository extends ElasticsearchRepository<ChatAuditLogDocument, String> {

    /**
     * Find chat logs by user ID with pagination.
     */
    Page<ChatAuditLogDocument> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    /**
     * Find chat logs by session ID.
     */
    List<ChatAuditLogDocument> findBySessionIdOrderByTimestampAsc(String sessionId);

    /**
     * Find chat logs by category ID with pagination.
     */
    Page<ChatAuditLogDocument> findByCategoryIdOrderByTimestampDesc(String categoryId, Pageable pageable);

    /**
     * Find chat logs by action type with pagination.
     */
    Page<ChatAuditLogDocument> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    /**
     * Find chat logs by selected tool with pagination.
     */
    Page<ChatAuditLogDocument> findBySelectedToolOrderByTimestampDesc(String selectedTool, Pageable pageable);

    /**
     * Find chat logs within a time range.
     */
    Page<ChatAuditLogDocument> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find failed chat operations.
     */
    Page<ChatAuditLogDocument> findBySuccessFalseOrderByTimestampDesc(Pageable pageable);

    /**
     * Find chat logs by user ID and time range.
     */
    Page<ChatAuditLogDocument> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
            String userId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find chat logs by category and time range.
     */
    Page<ChatAuditLogDocument> findByCategoryIdAndTimestampBetweenOrderByTimestampDesc(
            String categoryId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Count logs by user ID.
     */
    long countByUserId(String userId);

    /**
     * Count logs by category ID.
     */
    long countByCategoryId(String categoryId);

    /**
     * Count failed operations.
     */
    long countBySuccessFalse();

    /**
     * Count logs in time range.
     */
    long countByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
