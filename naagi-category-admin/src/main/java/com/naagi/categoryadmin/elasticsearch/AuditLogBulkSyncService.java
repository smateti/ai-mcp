package com.naagi.categoryadmin.elasticsearch;

import com.naagi.categoryadmin.model.AuditLog;
import com.naagi.categoryadmin.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for bulk syncing audit logs from H2 to Elasticsearch.
 * Provides initial migration and scheduled incremental sync capabilities.
 * Also syncs chat audit logs from the chat app service.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "naagi.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class AuditLogBulkSyncService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogElasticsearchService elasticsearchService;
    private final ChatAuditLogElasticsearchService chatAuditLogService;

    @Autowired
    public AuditLogBulkSyncService(
            AuditLogRepository auditLogRepository,
            AuditLogElasticsearchService elasticsearchService,
            ChatAuditLogElasticsearchService chatAuditLogService) {
        this.auditLogRepository = auditLogRepository;
        this.elasticsearchService = elasticsearchService;
        this.chatAuditLogService = chatAuditLogService;
    }

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private LocalDateTime lastSyncTime;

    /**
     * Perform a full sync of all audit logs from H2 to Elasticsearch.
     * Also syncs chat audit logs from the chat app service.
     *
     * @return Number of documents synced
     */
    public long fullSync() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.warn("Sync already in progress, skipping");
            return 0;
        }

        try {
            log.info("Starting full audit log sync to Elasticsearch...");
            long totalSynced = 0;

            // Sync admin audit logs from H2
            totalSynced += syncAdminAuditLogs();

            // Sync chat audit logs from chat app
            syncChatAuditLogs();

            lastSyncTime = LocalDateTime.now();
            log.info("Full sync completed. Total admin documents synced: {}", totalSynced);
            return totalSynced;

        } finally {
            syncInProgress.set(false);
        }
    }

    /**
     * Sync admin audit logs from H2 database.
     */
    private long syncAdminAuditLogs() {
        long totalSynced = 0;
        int page = 0;
        int pageSize = 500;

        Page<AuditLog> auditLogs;
        do {
            auditLogs = auditLogRepository.findAll(
                    PageRequest.of(page, pageSize, Sort.by(Sort.Direction.ASC, "timestamp")));

            if (!auditLogs.isEmpty()) {
                elasticsearchService.indexAuditLogs(auditLogs.getContent());
                totalSynced += auditLogs.getNumberOfElements();
                log.info("Synced admin audit page {} ({} documents, total: {})",
                        page, auditLogs.getNumberOfElements(), totalSynced);
            }
            page++;
        } while (auditLogs.hasNext());

        return totalSynced;
    }

    /**
     * Sync chat audit logs from chat app service (async).
     */
    private void syncChatAuditLogs() {
        log.info("Starting chat audit log sync...");
        chatAuditLogService.fullSync();
    }

    /**
     * Perform an incremental sync of audit logs created since the last sync.
     *
     * @return Number of documents synced
     */
    public long incrementalSync() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.debug("Sync already in progress, skipping incremental sync");
            return 0;
        }

        try {
            LocalDateTime since = lastSyncTime != null ? lastSyncTime : LocalDateTime.now().minusHours(1);
            log.debug("Starting incremental sync since {}", since);

            List<AuditLog> newLogs = auditLogRepository.findByTimestampAfter(since);

            if (!newLogs.isEmpty()) {
                elasticsearchService.indexAuditLogs(newLogs);
                log.info("Incremental sync completed. Synced {} new documents", newLogs.size());
            }

            lastSyncTime = LocalDateTime.now();
            return newLogs.size();

        } finally {
            syncInProgress.set(false);
        }
    }

    /**
     * Scheduled incremental sync (runs every 5 minutes by default).
     * Can be configured via naagi.elasticsearch.sync.interval property.
     * Syncs both admin audit logs and chat audit logs.
     */
    @Scheduled(fixedRateString = "${naagi.elasticsearch.sync.interval:300000}")
    public void scheduledSync() {
        try {
            // Sync admin audit logs (incremental)
            incrementalSync();

            // Also sync chat audit logs
            chatAuditLogService.fullSync();
        } catch (Exception e) {
            log.error("Scheduled sync failed", e);
        }
    }

    /**
     * Sync audit logs for a specific time range.
     *
     * @param start Start time (inclusive)
     * @param end   End time (inclusive)
     * @return Number of documents synced
     */
    public long syncTimeRange(LocalDateTime start, LocalDateTime end) {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.warn("Sync already in progress, skipping");
            return 0;
        }

        try {
            log.info("Syncing audit logs from {} to {}", start, end);

            List<AuditLog> logs = auditLogRepository.findByTimestampBetween(start, end);

            if (!logs.isEmpty()) {
                elasticsearchService.indexAuditLogs(logs);
                log.info("Time range sync completed. Synced {} documents", logs.size());
            }

            return logs.size();

        } finally {
            syncInProgress.set(false);
        }
    }

    /**
     * Check if a sync is currently in progress.
     */
    public boolean isSyncInProgress() {
        return syncInProgress.get();
    }

    /**
     * Get the last sync time.
     */
    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }
}
