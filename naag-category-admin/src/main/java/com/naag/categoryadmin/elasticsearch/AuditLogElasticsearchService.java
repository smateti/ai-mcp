package com.naag.categoryadmin.elasticsearch;

import com.naag.categoryadmin.model.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for syncing audit logs to Elasticsearch.
 * Provides async indexing and search capabilities.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naag.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class AuditLogElasticsearchService {

    private final AuditLogElasticsearchRepository elasticsearchRepository;

    @Value("${spring.application.name:naag-category-admin}")
    private String applicationName;

    @Value("${naag.elasticsearch.environment:development}")
    private String environment;

    private String hostname;

    /**
     * Index a single audit log to Elasticsearch asynchronously.
     */
    @Async
    public void indexAuditLog(AuditLog auditLog) {
        try {
            AuditLogDocument document = convertToDocument(auditLog);
            elasticsearchRepository.save(document);
            log.debug("Indexed audit log to Elasticsearch: {}", document.getId());
        } catch (Exception e) {
            log.error("Failed to index audit log to Elasticsearch", e);
        }
    }

    /**
     * Index multiple audit logs in bulk.
     */
    @Async
    public void indexAuditLogs(List<AuditLog> auditLogs) {
        try {
            List<AuditLogDocument> documents = auditLogs.stream()
                    .map(this::convertToDocument)
                    .toList();
            elasticsearchRepository.saveAll(documents);
            log.info("Bulk indexed {} audit logs to Elasticsearch", documents.size());
        } catch (Exception e) {
            log.error("Failed to bulk index audit logs to Elasticsearch", e);
        }
    }

    /**
     * Search audit logs with filters.
     */
    public Page<AuditLogDocument> search(String userId, String action, String entityType,
                                          String categoryId, int page, int size) {
        return elasticsearchRepository.searchByFilters(
                userId, action, entityType, categoryId,
                PageRequest.of(page, size));
    }

    /**
     * Full-text search on audit log details.
     */
    public Page<AuditLogDocument> searchByText(String searchTerm, int page, int size) {
        return elasticsearchRepository.searchByDetails(searchTerm, PageRequest.of(page, size));
    }

    /**
     * Get audit logs by user.
     */
    public Page<AuditLogDocument> getByUser(String userId, int page, int size) {
        return elasticsearchRepository.findByUserIdOrderByTimestampDesc(userId, PageRequest.of(page, size));
    }

    /**
     * Get audit logs by action.
     */
    public Page<AuditLogDocument> getByAction(String action, int page, int size) {
        return elasticsearchRepository.findByActionOrderByTimestampDesc(action, PageRequest.of(page, size));
    }

    /**
     * Get audit logs by category.
     */
    public Page<AuditLogDocument> getByCategory(String categoryId, int page, int size) {
        return elasticsearchRepository.findByCategoryIdOrderByTimestampDesc(categoryId, PageRequest.of(page, size));
    }

    /**
     * Get audit logs by time range.
     */
    public Page<AuditLogDocument> getByTimeRange(LocalDateTime start, LocalDateTime end, int page, int size) {
        return elasticsearchRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, PageRequest.of(page, size));
    }

    /**
     * Get failure logs since a given time.
     */
    public Page<AuditLogDocument> getFailuresSince(LocalDateTime since, int page, int size) {
        return elasticsearchRepository.findByStatusAndTimestampAfterOrderByTimestampDesc(
                "FAILURE", since, PageRequest.of(page, size));
    }

    /**
     * Count failures since a given time.
     */
    public long countFailuresSince(LocalDateTime since) {
        return elasticsearchRepository.countByStatusAndTimestampAfter("FAILURE", since);
    }

    /**
     * Delete old audit logs from Elasticsearch.
     */
    public void deleteOldLogs(LocalDateTime before) {
        try {
            elasticsearchRepository.deleteByTimestampBefore(before);
            log.info("Deleted audit logs from Elasticsearch older than {}", before);
        } catch (Exception e) {
            log.error("Failed to delete old audit logs from Elasticsearch", e);
        }
    }

    /**
     * Convert JPA entity to Elasticsearch document.
     */
    private AuditLogDocument convertToDocument(AuditLog auditLog) {
        return AuditLogDocument.builder()
                .id(auditLog.getId() != null ? auditLog.getId().toString() : UUID.randomUUID().toString())
                .userId(auditLog.getUserId())
                .action(auditLog.getAction())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .details(auditLog.getDetails())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .categoryId(auditLog.getCategoryId())
                .timestamp(auditLog.getTimestamp())
                .status(auditLog.getStatus() != null ? auditLog.getStatus().name() : "SUCCESS")
                .errorMessage(auditLog.getErrorMessage())
                .applicationName(applicationName)
                .environment(environment)
                .hostname(getHostname())
                .build();
    }

    private String getHostname() {
        if (hostname == null) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                hostname = "unknown";
            }
        }
        return hostname;
    }
}
