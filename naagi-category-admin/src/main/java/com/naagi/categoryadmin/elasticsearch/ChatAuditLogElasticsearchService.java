package com.naagi.categoryadmin.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.naagi.categoryadmin.client.ChatAppClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing chat audit logs in Elasticsearch.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "naagi.elasticsearch.enabled", havingValue = "true")
public class ChatAuditLogElasticsearchService {

    private final ChatAuditLogElasticsearchRepository repository;
    private final ChatAppClient chatAppClient;
    private final String environment;

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private volatile LocalDateTime lastSyncTime;
    private final AtomicInteger lastSyncCount = new AtomicInteger(0);

    private static final DateTimeFormatter[] TIMESTAMP_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    @Autowired
    public ChatAuditLogElasticsearchService(
            ChatAuditLogElasticsearchRepository repository,
            ChatAppClient chatAppClient,
            @Value("${naagi.elasticsearch.environment:development}") String environment) {
        this.repository = repository;
        this.chatAppClient = chatAppClient;
        this.environment = environment;
    }

    /**
     * Index a single chat audit log from JsonNode.
     */
    public void indexChatLog(JsonNode logNode) {
        try {
            ChatAuditLogDocument doc = convertToDocument(logNode);
            repository.save(doc);
            log.debug("Indexed chat audit log: {}", doc.getId());
        } catch (Exception e) {
            log.error("Failed to index chat audit log", e);
        }
    }

    /**
     * Bulk index chat audit logs.
     */
    public int bulkIndex(List<JsonNode> logs) {
        AtomicInteger count = new AtomicInteger(0);
        logs.forEach(logNode -> {
            try {
                ChatAuditLogDocument doc = convertToDocument(logNode);
                repository.save(doc);
                count.incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to index chat log: {}", logNode.path("id").asText(), e);
            }
        });
        return count.get();
    }

    /**
     * Full sync of all chat logs from the chat app.
     */
    @Async
    public void fullSync() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.warn("Chat audit log sync already in progress");
            return;
        }

        try {
            log.info("Starting full chat audit log sync to Elasticsearch");
            int totalSynced = 0;
            int page = 0;
            int pageSize = 100;

            while (true) {
                List<JsonNode> logs = chatAppClient.getAuditLogs("default-user", page, pageSize);
                if (logs.isEmpty()) {
                    break;
                }

                int synced = bulkIndex(logs);
                totalSynced += synced;
                log.info("Synced page {} with {} chat logs", page, synced);

                if (logs.size() < pageSize) {
                    break;
                }
                page++;
            }

            lastSyncTime = LocalDateTime.now();
            lastSyncCount.set(totalSynced);
            log.info("Full chat audit log sync completed. Total synced: {}", totalSynced);
        } catch (Exception e) {
            log.error("Error during full chat audit log sync", e);
        } finally {
            syncInProgress.set(false);
        }
    }

    /**
     * Search chat logs with various filters.
     */
    public Page<ChatAuditLogDocument> searchChatLogs(
            String userId,
            String categoryId,
            String action,
            String selectedTool,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        if (userId != null && from != null && to != null) {
            return repository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(userId, from, to, pageable);
        }
        if (categoryId != null && from != null && to != null) {
            return repository.findByCategoryIdAndTimestampBetweenOrderByTimestampDesc(categoryId, from, to, pageable);
        }
        if (userId != null) {
            return repository.findByUserIdOrderByTimestampDesc(userId, pageable);
        }
        if (categoryId != null) {
            return repository.findByCategoryIdOrderByTimestampDesc(categoryId, pageable);
        }
        if (action != null) {
            return repository.findByActionOrderByTimestampDesc(action, pageable);
        }
        if (selectedTool != null) {
            return repository.findBySelectedToolOrderByTimestampDesc(selectedTool, pageable);
        }
        if (from != null && to != null) {
            return repository.findByTimestampBetweenOrderByTimestampDesc(from, to, pageable);
        }

        // Default: return all with pagination
        return repository.findAll(pageable);
    }

    /**
     * Get chat logs for a specific session.
     */
    public List<ChatAuditLogDocument> getSessionLogs(String sessionId) {
        return repository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    /**
     * Get failed chat operations.
     */
    public Page<ChatAuditLogDocument> getFailedOperations(Pageable pageable) {
        return repository.findBySuccessFalseOrderByTimestampDesc(pageable);
    }

    /**
     * Get sync status.
     */
    public boolean isSyncInProgress() {
        return syncInProgress.get();
    }

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public int getLastSyncCount() {
        return lastSyncCount.get();
    }

    /**
     * Get statistics.
     */
    public ChatAuditStats getStats() {
        long totalCount = repository.count();
        long failedCount = repository.countBySuccessFalse();
        LocalDateTime now = LocalDateTime.now();
        long last24h = repository.countByTimestampBetween(now.minusHours(24), now);

        return ChatAuditStats.builder()
                .totalLogs(totalCount)
                .failedOperations(failedCount)
                .logsLast24Hours(last24h)
                .lastSyncTime(lastSyncTime)
                .syncInProgress(syncInProgress.get())
                .build();
    }

    /**
     * Convert JsonNode to ChatAuditLogDocument.
     */
    private ChatAuditLogDocument convertToDocument(JsonNode node) {
        return ChatAuditLogDocument.builder()
                .id(node.path("id").asText())
                .userId(getTextOrNull(node, "userId"))
                .sessionId(getTextOrNull(node, "sessionId"))
                .messageId(getTextOrNull(node, "messageId"))
                .action(getTextOrNull(node, "action"))
                .userQuestion(getTextOrNull(node, "userQuestion"))
                .assistantResponse(getTextOrNull(node, "assistantResponse"))
                .categoryId(getTextOrNull(node, "categoryId"))
                .categoryName(getTextOrNull(node, "categoryName"))
                .intent(getTextOrNull(node, "intent"))
                .selectedTool(getTextOrNull(node, "selectedTool"))
                .confidence(node.has("confidence") && !node.get("confidence").isNull()
                        ? node.get("confidence").asDouble() : null)
                .processingTimeMs(node.has("processingTimeMs") && !node.get("processingTimeMs").isNull()
                        ? node.get("processingTimeMs").asLong() : null)
                .inputTokens(node.has("inputTokens") && !node.get("inputTokens").isNull()
                        ? node.get("inputTokens").asInt() : null)
                .outputTokens(node.has("outputTokens") && !node.get("outputTokens").isNull()
                        ? node.get("outputTokens").asInt() : null)
                .success(node.has("success") && !node.get("success").isNull()
                        ? node.get("success").asBoolean() : null)
                .errorMessage(getTextOrNull(node, "errorMessage"))
                .clientIp(getTextOrNull(node, "clientIp"))
                .userAgent(getTextOrNull(node, "userAgent"))
                .timestamp(parseTimestamp(node.path("timestamp").asText()))
                .environment(environment)
                .build();
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isNull() || fieldNode.isMissingNode() ? null : fieldNode.asText();
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return LocalDateTime.now();
        }

        for (DateTimeFormatter formatter : TIMESTAMP_FORMATTERS) {
            try {
                return LocalDateTime.parse(timestamp, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        log.warn("Could not parse timestamp: {}, using current time", timestamp);
        return LocalDateTime.now();
    }

    /**
     * Stats DTO for chat audit logs.
     */
    @lombok.Data
    @lombok.Builder
    public static class ChatAuditStats {
        private long totalLogs;
        private long failedOperations;
        private long logsLast24Hours;
        private LocalDateTime lastSyncTime;
        private boolean syncInProgress;
    }
}
