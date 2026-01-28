package com.naagi.chat.service;

import com.naagi.chat.config.ElasticsearchHealthChecker;
import com.naagi.chat.config.PersistenceProperties;
import com.naagi.chat.config.PersistenceProperties.StorageType;
import com.naagi.chat.entity.AuditLogDocument;
import com.naagi.chat.entity.AuditLogEntity;
import com.naagi.chat.repository.AuditLogElasticsearchRepository;
import com.naagi.chat.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AuditService {

    private final PersistenceProperties properties;
    private final AuditLogRepository h2Repository;
    private final ElasticsearchHealthChecker esHealthChecker;  // Manages ES availability dynamically

    @Autowired
    public AuditService(PersistenceProperties properties,
                        AuditLogRepository h2Repository,
                        @Autowired(required = false) ElasticsearchHealthChecker esHealthChecker) {
        this.properties = properties;
        this.h2Repository = h2Repository;
        this.esHealthChecker = esHealthChecker;

        if (esHealthChecker == null) {
            log.info("Elasticsearch health checker not available - audit will use H2 only");
        } else {
            log.info("Elasticsearch health checker enabled - ES availability will be checked periodically");
        }
    }

    /**
     * Get ES repository from health checker (may return null if ES unavailable)
     */
    private AuditLogElasticsearchRepository getEsRepository() {
        return esHealthChecker != null ? esHealthChecker.getRepository() : null;
    }

    public void logChatStarted(String userId, String sessionId, String categoryId, String categoryName, String clientIp, String userAgent) {
        if (!properties.getAudit().isEnabled()) return;

        AuditLogEntity entity = AuditLogEntity.builder()
                .userId(userId)
                .sessionId(sessionId)
                .action("CHAT_STARTED")
                .categoryId(categoryId)
                .categoryName(categoryName)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();

        saveAuditLog(entity);
    }

    public void logMessageProcessed(String userId, String sessionId, String messageId,
                                     String userQuestion, String assistantResponse,
                                     String categoryId, String categoryName,
                                     String intent, String selectedTool, Double confidence,
                                     long processingTimeMs, boolean success, String errorMessage,
                                     String clientIp, String userAgent) {
        logMessageProcessed(userId, sessionId, messageId, userQuestion, assistantResponse,
                categoryId, categoryName, intent, selectedTool, null, confidence,
                processingTimeMs, success, errorMessage, clientIp, userAgent);
    }

    public void logMessageProcessed(String userId, String sessionId, String messageId,
                                     String userQuestion, String assistantResponse,
                                     String categoryId, String categoryName,
                                     String intent, String selectedTool, String systemPrompt, Double confidence,
                                     long processingTimeMs, boolean success, String errorMessage,
                                     String clientIp, String userAgent) {
        if (!properties.getAudit().isEnabled()) return;

        AuditLogEntity entity = AuditLogEntity.builder()
                .userId(userId)
                .sessionId(sessionId)
                .messageId(messageId)
                .action("MESSAGE_PROCESSED")
                .userQuestion(properties.getAudit().isLogPrompts() ? userQuestion : "[REDACTED]")
                .systemPrompt(properties.getAudit().isLogPrompts() ? systemPrompt : "[REDACTED]")
                .assistantResponse(properties.getAudit().isLogResponses() ? assistantResponse : "[REDACTED]")
                .categoryId(categoryId)
                .categoryName(categoryName)
                .intent(intent)
                .selectedTool(selectedTool)
                .confidence(confidence)
                .processingTimeMs(processingTimeMs)
                .success(success)
                .errorMessage(errorMessage)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .timestamp(LocalDateTime.now())
                .build();

        saveAuditLog(entity);
    }

    public void logError(String userId, String sessionId, String action, String errorMessage, String stackTrace,
                         String clientIp, String userAgent) {
        if (!properties.getAudit().isEnabled()) return;

        AuditLogEntity entity = AuditLogEntity.builder()
                .userId(userId)
                .sessionId(sessionId)
                .action(action)
                .success(false)
                .errorMessage(errorMessage)
                .errorStackTrace(stackTrace != null && stackTrace.length() > 500 ? stackTrace.substring(0, 500) : stackTrace)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .timestamp(LocalDateTime.now())
                .build();

        saveAuditLog(entity);
    }

    private void saveAuditLog(AuditLogEntity entity) {
        StorageType type = properties.getAudit().getType();

        if (type == StorageType.H2 || type == StorageType.BOTH) {
            try {
                h2Repository.save(entity);
                log.debug("Audit log saved to H2: {}", entity.getAction());
            } catch (Exception e) {
                log.error("Failed to save audit log to H2", e);
            }
        }

        if (type == StorageType.ELASTICSEARCH || type == StorageType.BOTH) {
            AuditLogElasticsearchRepository esRepository = getEsRepository();
            if (esRepository != null) {
                try {
                    AuditLogDocument doc = convertToDocument(entity);
                    esRepository.save(doc);
                    log.debug("Audit log saved to Elasticsearch: {}", entity.getAction());
                } catch (Exception e) {
                    log.warn("Failed to save audit log to Elasticsearch: {}", e.getMessage());
                }
            }
        }
    }

    private AuditLogDocument convertToDocument(AuditLogEntity entity) {
        return AuditLogDocument.builder()
                .id(entity.getId() != null ? entity.getId() : UUID.randomUUID().toString())
                .userId(entity.getUserId())
                .sessionId(entity.getSessionId())
                .messageId(entity.getMessageId())
                .action(entity.getAction())
                .userQuestion(entity.getUserQuestion())
                .systemPrompt(entity.getSystemPrompt())
                .assistantResponse(entity.getAssistantResponse())
                .categoryId(entity.getCategoryId())
                .categoryName(entity.getCategoryName())
                .intent(entity.getIntent())
                .selectedTool(entity.getSelectedTool())
                .confidence(entity.getConfidence())
                .processingTimeMs(entity.getProcessingTimeMs())
                .inputTokens(entity.getInputTokens())
                .outputTokens(entity.getOutputTokens())
                .success(entity.getSuccess())
                .errorMessage(entity.getErrorMessage())
                .errorStackTrace(entity.getErrorStackTrace())
                .clientIp(entity.getClientIp())
                .userAgent(entity.getUserAgent())
                .timestamp(entity.getTimestamp())
                .build();
    }

    // Query methods
    public List<AuditLogEntity> getAuditLogs(String userId, int page, int size) {
        StorageType type = properties.getAudit().getType();
        AuditLogElasticsearchRepository esRepository = getEsRepository();

        if (type == StorageType.ELASTICSEARCH && esRepository != null) {
            try {
                Page<AuditLogDocument> docs = esRepository.findByUserIdOrderByTimestampDesc(userId, PageRequest.of(page, size));
                return docs.stream().map(this::convertToEntity).collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Elasticsearch query failed, falling back to H2: {}", e.getMessage());
            }
        }

        return h2Repository.findByUserIdOrderByTimestampDesc(userId, PageRequest.of(page, size)).getContent();
    }

    public List<AuditLogEntity> getSessionAuditLogs(String sessionId) {
        StorageType type = properties.getAudit().getType();
        AuditLogElasticsearchRepository esRepository = getEsRepository();

        if (type == StorageType.ELASTICSEARCH && esRepository != null) {
            try {
                return esRepository.findBySessionIdOrderByTimestampAsc(sessionId).stream()
                        .map(this::convertToEntity)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Elasticsearch query failed, falling back to H2: {}", e.getMessage());
            }
        }

        return h2Repository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    public List<AuditLogEntity> getAuditLogsByDateRange(String userId, LocalDateTime start, LocalDateTime end) {
        StorageType type = properties.getAudit().getType();
        AuditLogElasticsearchRepository esRepository = getEsRepository();

        if (type == StorageType.ELASTICSEARCH && esRepository != null) {
            try {
                return esRepository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(userId, start, end).stream()
                        .map(this::convertToEntity)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Elasticsearch query failed, falling back to H2: {}", e.getMessage());
            }
        }

        return h2Repository.findByUserIdAndDateRange(userId, start, end);
    }

    public List<AuditLogEntity> getFailedOperations(String userId) {
        StorageType type = properties.getAudit().getType();
        AuditLogElasticsearchRepository esRepository = getEsRepository();

        if (type == StorageType.ELASTICSEARCH && esRepository != null) {
            try {
                return esRepository.findByUserIdAndSuccessFalseOrderByTimestampDesc(userId).stream()
                        .map(this::convertToEntity)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Elasticsearch query failed, falling back to H2: {}", e.getMessage());
            }
        }

        return h2Repository.findFailedByUserId(userId);
    }

    public Map<String, Object> getUserStats(String userId) {
        Double avgProcessingTime = h2Repository.getAverageProcessingTime(userId);
        long totalMessages = h2Repository.countByUserIdAndAction(userId, "MESSAGE_PROCESSED");
        List<Object[]> toolUsage = h2Repository.getToolUsageStats(userId);

        Map<String, Long> toolStats = toolUsage.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));

        return Map.of(
                "averageProcessingTimeMs", avgProcessingTime != null ? avgProcessingTime : 0,
                "totalMessages", totalMessages,
                "toolUsage", toolStats
        );
    }

    private AuditLogEntity convertToEntity(AuditLogDocument doc) {
        return AuditLogEntity.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .sessionId(doc.getSessionId())
                .messageId(doc.getMessageId())
                .action(doc.getAction())
                .userQuestion(doc.getUserQuestion())
                .systemPrompt(doc.getSystemPrompt())
                .assistantResponse(doc.getAssistantResponse())
                .categoryId(doc.getCategoryId())
                .categoryName(doc.getCategoryName())
                .intent(doc.getIntent())
                .selectedTool(doc.getSelectedTool())
                .confidence(doc.getConfidence())
                .processingTimeMs(doc.getProcessingTimeMs())
                .inputTokens(doc.getInputTokens())
                .outputTokens(doc.getOutputTokens())
                .success(doc.getSuccess())
                .errorMessage(doc.getErrorMessage())
                .errorStackTrace(doc.getErrorStackTrace())
                .clientIp(doc.getClientIp())
                .userAgent(doc.getUserAgent())
                .timestamp(doc.getTimestamp())
                .build();
    }

    /**
     * Find the answer from audit trail for a given question.
     * First tries exact match, then falls back to containing match.
     */
    public java.util.Optional<AuditLogEntity> findAnswerByQuestion(String question) {
        if (question == null || question.isBlank()) {
            return java.util.Optional.empty();
        }

        // Try exact match first
        List<AuditLogEntity> exactMatches = h2Repository.findByExactQuestion(question);
        if (!exactMatches.isEmpty()) {
            return java.util.Optional.of(exactMatches.get(0));
        }

        // Try containing match
        List<AuditLogEntity> containingMatches = h2Repository.findByQuestionContaining(question);
        if (!containingMatches.isEmpty()) {
            return java.util.Optional.of(containingMatches.get(0));
        }

        return java.util.Optional.empty();
    }
}
