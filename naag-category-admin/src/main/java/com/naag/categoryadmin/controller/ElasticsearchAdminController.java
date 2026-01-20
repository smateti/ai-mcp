package com.naag.categoryadmin.controller;

import com.naag.categoryadmin.elasticsearch.AuditLogBulkSyncService;
import com.naag.categoryadmin.elasticsearch.AuditLogDocument;
import com.naag.categoryadmin.elasticsearch.AuditLogElasticsearchService;
import com.naag.categoryadmin.elasticsearch.ChatAuditLogDocument;
import com.naag.categoryadmin.elasticsearch.ChatAuditLogElasticsearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Elasticsearch audit log management.
 * Only enabled when Elasticsearch is configured.
 */
@RestController
@RequestMapping("/api/elasticsearch")
@Slf4j
@ConditionalOnProperty(name = "naag.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchAdminController {

    private final AuditLogElasticsearchService elasticsearchService;
    private final AuditLogBulkSyncService bulkSyncService;
    private final ChatAuditLogElasticsearchService chatAuditLogService;

    @Autowired
    public ElasticsearchAdminController(
            @Autowired(required = false) AuditLogElasticsearchService elasticsearchService,
            @Autowired(required = false) AuditLogBulkSyncService bulkSyncService,
            @Autowired(required = false) ChatAuditLogElasticsearchService chatAuditLogService) {
        this.elasticsearchService = elasticsearchService;
        this.bulkSyncService = bulkSyncService;
        this.chatAuditLogService = chatAuditLogService;
    }

    /**
     * Get sync status.
     */
    @GetMapping("/sync/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("syncInProgress", bulkSyncService.isSyncInProgress());
        status.put("lastSyncTime", bulkSyncService.getLastSyncTime());
        status.put("elasticsearchEnabled", true);
        return ResponseEntity.ok(status);
    }

    /**
     * Trigger a full sync from H2 to Elasticsearch.
     */
    @PostMapping("/sync/full")
    public ResponseEntity<Map<String, Object>> triggerFullSync() {
        if (bulkSyncService.isSyncInProgress()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Sync already in progress",
                    "syncInProgress", true
            ));
        }

        // Run async in background
        new Thread(() -> {
            try {
                bulkSyncService.fullSync();
            } catch (Exception e) {
                log.error("Full sync failed", e);
            }
        }).start();

        return ResponseEntity.accepted().body(Map.of(
                "message", "Full sync started",
                "syncInProgress", true
        ));
    }

    /**
     * Trigger an incremental sync.
     */
    @PostMapping("/sync/incremental")
    public ResponseEntity<Map<String, Object>> triggerIncrementalSync() {
        if (bulkSyncService.isSyncInProgress()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Sync already in progress",
                    "syncInProgress", true
            ));
        }

        long synced = bulkSyncService.incrementalSync();
        return ResponseEntity.ok(Map.of(
                "message", "Incremental sync completed",
                "documentsSynced", synced
        ));
    }

    /**
     * Sync audit logs for a specific time range.
     */
    @PostMapping("/sync/range")
    public ResponseEntity<Map<String, Object>> syncTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        if (bulkSyncService.isSyncInProgress()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Sync already in progress",
                    "syncInProgress", true
            ));
        }

        long synced = bulkSyncService.syncTimeRange(start, end);
        return ResponseEntity.ok(Map.of(
                "message", "Time range sync completed",
                "documentsSynced", synced,
                "start", start.toString(),
                "end", end.toString()
        ));
    }

    /**
     * Search audit logs in Elasticsearch.
     */
    @GetMapping("/audit-logs/search")
    public ResponseEntity<Page<AuditLogDocument>> searchAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLogDocument> results = elasticsearchService.search(
                userId, action, entityType, categoryId, page, size);
        return ResponseEntity.ok(results);
    }

    /**
     * Full-text search on audit log details.
     */
    @GetMapping("/audit-logs/search/text")
    public ResponseEntity<Page<AuditLogDocument>> searchByText(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLogDocument> results = elasticsearchService.searchByText(query, page, size);
        return ResponseEntity.ok(results);
    }

    /**
     * Get audit logs by user from Elasticsearch.
     */
    @GetMapping("/audit-logs/user/{userId}")
    public ResponseEntity<Page<AuditLogDocument>> getByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLogDocument> results = elasticsearchService.getByUser(userId, page, size);
        return ResponseEntity.ok(results);
    }

    /**
     * Get audit logs by action from Elasticsearch.
     */
    @GetMapping("/audit-logs/action/{action}")
    public ResponseEntity<Page<AuditLogDocument>> getByAction(
            @PathVariable String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLogDocument> results = elasticsearchService.getByAction(action, page, size);
        return ResponseEntity.ok(results);
    }

    /**
     * Get failure logs from Elasticsearch.
     */
    @GetMapping("/audit-logs/failures")
    public ResponseEntity<Page<AuditLogDocument>> getFailures(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Page<AuditLogDocument> results = elasticsearchService.getFailuresSince(since, page, size);
        return ResponseEntity.ok(results);
    }

    /**
     * Get failure count from Elasticsearch.
     */
    @GetMapping("/audit-logs/failures/count")
    public ResponseEntity<Map<String, Object>> getFailureCount(
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        long count = elasticsearchService.countFailuresSince(since);
        return ResponseEntity.ok(Map.of(
                "failureCount", count,
                "periodHours", hours,
                "since", since.toString()
        ));
    }

    // ==================== Chat Audit Log Endpoints ====================

    /**
     * Search chat audit logs in Elasticsearch.
     */
    @GetMapping("/chat-logs/search")
    public ResponseEntity<Page<ChatAuditLogDocument>> searchChatLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String selectedTool,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ChatAuditLogDocument> results = chatAuditLogService.searchChatLogs(
                userId, categoryId, action, selectedTool, from, to, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    /**
     * Get chat logs for a specific session.
     */
    @GetMapping("/chat-logs/session/{sessionId}")
    public ResponseEntity<List<ChatAuditLogDocument>> getSessionChatLogs(
            @PathVariable String sessionId) {
        List<ChatAuditLogDocument> results = chatAuditLogService.getSessionLogs(sessionId);
        return ResponseEntity.ok(results);
    }

    /**
     * Get chat logs by user.
     */
    @GetMapping("/chat-logs/user/{userId}")
    public ResponseEntity<Page<ChatAuditLogDocument>> getChatLogsByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ChatAuditLogDocument> results = chatAuditLogService.searchChatLogs(
                userId, null, null, null, null, null, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    /**
     * Get chat logs by category.
     */
    @GetMapping("/chat-logs/category/{categoryId}")
    public ResponseEntity<Page<ChatAuditLogDocument>> getChatLogsByCategory(
            @PathVariable String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ChatAuditLogDocument> results = chatAuditLogService.searchChatLogs(
                null, categoryId, null, null, null, null, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    /**
     * Get chat logs by tool.
     */
    @GetMapping("/chat-logs/tool/{toolName}")
    public ResponseEntity<Page<ChatAuditLogDocument>> getChatLogsByTool(
            @PathVariable String toolName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ChatAuditLogDocument> results = chatAuditLogService.searchChatLogs(
                null, null, null, toolName, null, null, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    /**
     * Get failed chat operations.
     */
    @GetMapping("/chat-logs/failures")
    public ResponseEntity<Page<ChatAuditLogDocument>> getChatFailures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ChatAuditLogDocument> results = chatAuditLogService.getFailedOperations(PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    /**
     * Get chat audit log statistics.
     */
    @GetMapping("/chat-logs/stats")
    public ResponseEntity<ChatAuditLogElasticsearchService.ChatAuditStats> getChatStats() {
        return ResponseEntity.ok(chatAuditLogService.getStats());
    }

    /**
     * Trigger sync of chat logs only.
     */
    @PostMapping("/sync/chat")
    public ResponseEntity<Map<String, Object>> triggerChatSync() {
        if (chatAuditLogService.isSyncInProgress()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Chat sync already in progress",
                    "syncInProgress", true
            ));
        }

        chatAuditLogService.fullSync();
        return ResponseEntity.accepted().body(Map.of(
                "message", "Chat audit log sync started",
                "syncInProgress", true
        ));
    }

    /**
     * Get chat sync status.
     */
    @GetMapping("/sync/chat/status")
    public ResponseEntity<Map<String, Object>> getChatSyncStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("syncInProgress", chatAuditLogService.isSyncInProgress());
        status.put("lastSyncTime", chatAuditLogService.getLastSyncTime());
        status.put("lastSyncCount", chatAuditLogService.getLastSyncCount());
        return ResponseEntity.ok(status);
    }
}
