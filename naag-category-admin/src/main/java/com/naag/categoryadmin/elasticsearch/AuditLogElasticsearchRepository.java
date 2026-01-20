package com.naag.categoryadmin.elasticsearch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch repository for audit log documents.
 * Provides advanced search capabilities for audit trail data.
 */
@Repository
public interface AuditLogElasticsearchRepository extends ElasticsearchRepository<AuditLogDocument, String> {

    // Find by user
    Page<AuditLogDocument> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    // Find by action
    Page<AuditLogDocument> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    // Find by entity
    Page<AuditLogDocument> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId, Pageable pageable);

    // Find by category
    Page<AuditLogDocument> findByCategoryIdOrderByTimestampDesc(String categoryId, Pageable pageable);

    // Find by status
    Page<AuditLogDocument> findByStatusOrderByTimestampDesc(String status, Pageable pageable);

    // Find by timestamp range
    Page<AuditLogDocument> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Find failures
    Page<AuditLogDocument> findByStatusAndTimestampAfterOrderByTimestampDesc(String status, LocalDateTime since, Pageable pageable);

    // Count by action
    long countByAction(String action);

    // Count by user
    long countByUserId(String userId);

    // Count by status since timestamp
    long countByStatusAndTimestampAfter(String status, LocalDateTime since);

    // Full-text search on details
    @Query("{\"bool\": {\"must\": [{\"match\": {\"details\": \"?0\"}}]}}")
    Page<AuditLogDocument> searchByDetails(String searchTerm, Pageable pageable);

    // Combined search query
    @Query("{\"bool\": {" +
            "\"must\": [" +
            "  {\"bool\": {\"should\": [{\"term\": {\"userId\": \"?0\"}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"_source.userId_filter\"}}}}]}}," +
            "  {\"bool\": {\"should\": [{\"term\": {\"action\": \"?1\"}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"_source.action_filter\"}}}}]}}," +
            "  {\"bool\": {\"should\": [{\"term\": {\"entityType\": \"?2\"}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"_source.entityType_filter\"}}}}]}}," +
            "  {\"bool\": {\"should\": [{\"term\": {\"categoryId\": \"?3\"}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"_source.categoryId_filter\"}}}}]}}" +
            "]" +
            "}}")
    Page<AuditLogDocument> searchByFilters(String userId, String action, String entityType, String categoryId, Pageable pageable);

    // Delete old logs
    void deleteByTimestampBefore(LocalDateTime before);

    // Get distinct values for filters
    List<AuditLogDocument> findDistinctByUserId();
}
