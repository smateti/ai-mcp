package com.naag.categoryadmin.service;

import com.naag.categoryadmin.elasticsearch.AuditLogElasticsearchService;
import com.naag.categoryadmin.model.AuditLog;
import com.naag.categoryadmin.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogElasticsearchService elasticsearchService;

    @Autowired
    public AuditLogService(AuditLogRepository auditLogRepository,
                           @Autowired(required = false) AuditLogElasticsearchService elasticsearchService) {
        this.auditLogRepository = auditLogRepository;
        this.elasticsearchService = elasticsearchService;
    }

    // Action constants
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_CHAT = "CHAT";
    public static final String ACTION_TOOL_CALL = "TOOL_CALL";
    public static final String ACTION_RAG_QUERY = "RAG_QUERY";
    public static final String ACTION_DOCUMENT_UPLOAD = "DOCUMENT_UPLOAD";
    public static final String ACTION_DOCUMENT_DELETE = "DOCUMENT_DELETE";
    public static final String ACTION_DOCUMENT_APPROVE = "DOCUMENT_APPROVE";
    public static final String ACTION_DOCUMENT_RETRY = "DOCUMENT_RETRY";
    public static final String ACTION_DOCUMENT_CHAT = "DOCUMENT_CHAT";
    public static final String ACTION_GENERATE_QA = "GENERATE_QA";
    public static final String ACTION_FAQ_SELECT = "FAQ_SELECT";
    public static final String ACTION_FAQ_APPROVE = "FAQ_APPROVE";
    public static final String ACTION_TOOL_REGISTER = "TOOL_REGISTER";
    public static final String ACTION_TOOL_UPDATE = "TOOL_UPDATE";
    public static final String ACTION_TOOL_DELETE = "TOOL_DELETE";
    public static final String ACTION_TOOL_ADD_TO_CATEGORY = "TOOL_ADD_TO_CATEGORY";
    public static final String ACTION_TOOL_REMOVE_FROM_CATEGORY = "TOOL_REMOVE_FROM_CATEGORY";
    public static final String ACTION_PARAMETER_OVERRIDE = "PARAMETER_OVERRIDE";
    public static final String ACTION_CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String ACTION_CATEGORY_UPDATE = "CATEGORY_UPDATE";
    public static final String ACTION_CATEGORY_DELETE = "CATEGORY_DELETE";
    public static final String ACTION_SETUP_RUN = "SETUP_RUN";
    public static final String ACTION_OPENAPI_IMPORT = "OPENAPI_IMPORT";

    // Entity type constants
    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_CHAT = "CHAT";
    public static final String ENTITY_TOOL = "TOOL";
    public static final String ENTITY_DOCUMENT = "DOCUMENT";
    public static final String ENTITY_CATEGORY = "CATEGORY";
    public static final String ENTITY_SYSTEM = "SYSTEM";

    @Async
    public void logAsync(String userId, String action, String entityType, String entityId,
                         String details, String categoryId) {
        log(userId, action, entityType, entityId, details, categoryId, AuditLog.AuditStatus.SUCCESS, null);
    }

    @Async
    public void logFailureAsync(String userId, String action, String entityType, String entityId,
                                String details, String categoryId, String errorMessage) {
        log(userId, action, entityType, entityId, details, categoryId, AuditLog.AuditStatus.FAILURE, errorMessage);
    }

    public void log(String userId, String action, String entityType, String entityId,
                    String details, String categoryId, AuditLog.AuditStatus status, String errorMessage) {
        try {
            String ipAddress = null;
            String userAgent = null;

            // Try to get request info from current context
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    ipAddress = getClientIp(request);
                    userAgent = request.getHeader("User-Agent");
                    if (userAgent != null && userAgent.length() > 500) {
                        userAgent = userAgent.substring(0, 500);
                    }
                }
            } catch (Exception e) {
                log.trace("Could not get request attributes", e);
            }

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId != null ? userId : "anonymous")
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(truncate(details, 2000))
                    .categoryId(categoryId)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status(status)
                    .errorMessage(truncate(errorMessage, 2000))
                    .timestamp(LocalDateTime.now())
                    .build();

            AuditLog savedLog = auditLogRepository.save(auditLog);
            log.debug("Audit log saved: {} {} {} {}", action, entityType, entityId, status);

            // Sync to Elasticsearch if enabled
            if (elasticsearchService != null) {
                elasticsearchService.indexAuditLog(savedLog);
            }
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }

    // Query methods
    public Page<AuditLog> getAuditLogs(int page, int size) {
        return auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size));
    }

    public Page<AuditLog> getAuditLogsByUser(String userId, int page, int size) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, PageRequest.of(page, size));
    }

    public Page<AuditLog> getAuditLogsByAction(String action, int page, int size) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action, PageRequest.of(page, size));
    }

    public Page<AuditLog> getAuditLogsByEntity(String entityType, String entityId, int page, int size) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId, PageRequest.of(page, size));
    }

    public Page<AuditLog> getAuditLogsByCategory(String categoryId, int page, int size) {
        return auditLogRepository.findByCategoryIdOrderByTimestampDesc(categoryId, PageRequest.of(page, size));
    }

    public Page<AuditLog> searchAuditLogs(String userId, String action, String entityType,
                                           String categoryId, LocalDateTime startDate, LocalDateTime endDate,
                                           int page, int size) {
        return auditLogRepository.findByFilters(
                userId, action, entityType, categoryId, startDate, endDate,
                PageRequest.of(page, size));
    }

    public List<String> getDistinctUserIds() {
        return auditLogRepository.findDistinctUserIds();
    }

    public List<String> getDistinctActions() {
        return auditLogRepository.findDistinctActions();
    }

    public List<String> getDistinctEntityTypes() {
        return auditLogRepository.findDistinctEntityTypes();
    }

    public Map<String, Object> getStatistics(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalLogs", auditLogRepository.countSince(since));
        stats.put("failures", auditLogRepository.countFailuresSince(since));

        // Action breakdown
        List<Object[]> actionCounts = auditLogRepository.countByActionSince(since);
        Map<String, Long> actionBreakdown = new HashMap<>();
        for (Object[] row : actionCounts) {
            actionBreakdown.put((String) row[0], (Long) row[1]);
        }
        stats.put("actionBreakdown", actionBreakdown);

        // User activity
        List<Object[]> userCounts = auditLogRepository.countByUserSince(since);
        Map<String, Long> userActivity = new HashMap<>();
        for (Object[] row : userCounts) {
            userActivity.put((String) row[0], (Long) row[1]);
        }
        stats.put("userActivity", userActivity);

        stats.put("periodHours", hours);
        return stats;
    }

    // Convenience methods for common operations
    public void logChat(String userId, String sessionId, String message, String categoryId) {
        logAsync(userId, ACTION_CHAT, ENTITY_CHAT, sessionId,
                "Message: " + truncate(message, 200), categoryId);
    }

    public void logToolCall(String userId, String toolId, String parameters, String categoryId, boolean success, String error) {
        if (success) {
            logAsync(userId, ACTION_TOOL_CALL, ENTITY_TOOL, toolId,
                    "Parameters: " + truncate(parameters, 500), categoryId);
        } else {
            logFailureAsync(userId, ACTION_TOOL_CALL, ENTITY_TOOL, toolId,
                    "Parameters: " + truncate(parameters, 500), categoryId, error);
        }
    }

    public void logDocumentUpload(String userId, String docId, String title, String categoryId) {
        logAsync(userId, ACTION_DOCUMENT_UPLOAD, ENTITY_DOCUMENT, docId,
                "Title: " + title, categoryId);
    }

    public void logDocumentDelete(String userId, String docId, String categoryId) {
        logAsync(userId, ACTION_DOCUMENT_DELETE, ENTITY_DOCUMENT, docId, null, categoryId);
    }

    public void logToolRegister(String userId, String toolId, String toolName, String categoryId) {
        logAsync(userId, ACTION_TOOL_REGISTER, ENTITY_TOOL, toolId,
                "Tool: " + toolName, categoryId);
    }

    public void logOpenApiImport(String userId, String source, int toolCount, String categoryId) {
        logAsync(userId, ACTION_OPENAPI_IMPORT, ENTITY_TOOL, null,
                "Imported " + toolCount + " tools from " + source, categoryId);
    }

    public void logCategoryCreate(String userId, String categoryId, String categoryName) {
        logAsync(userId, ACTION_CATEGORY_CREATE, ENTITY_CATEGORY, categoryId,
                "Category: " + categoryName, categoryId);
    }

    public void logCategoryUpdate(String userId, String categoryId, String categoryName) {
        logAsync(userId, ACTION_CATEGORY_UPDATE, ENTITY_CATEGORY, categoryId,
                "Category: " + categoryName, categoryId);
    }

    public void logCategoryDelete(String userId, String categoryId, String categoryName) {
        logAsync(userId, ACTION_CATEGORY_DELETE, ENTITY_CATEGORY, categoryId,
                "Category: " + categoryName, null);
    }

    public void logSetupRun(String userId) {
        logAsync(userId, ACTION_SETUP_RUN, ENTITY_SYSTEM, null,
                "Setup initialization triggered", null);
    }

    // Document workflow actions
    public void logDocumentApprove(String userId, String docId, String categoryId) {
        logAsync(userId, ACTION_DOCUMENT_APPROVE, ENTITY_DOCUMENT, docId,
                "Document approved and moved to RAG", categoryId);
    }

    public void logDocumentRetry(String userId, String docId, String categoryId) {
        logAsync(userId, ACTION_DOCUMENT_RETRY, ENTITY_DOCUMENT, docId,
                "Document reprocessing triggered", categoryId);
    }

    public void logDocumentChat(String userId, String docId, String question, String categoryId) {
        logAsync(userId, ACTION_DOCUMENT_CHAT, ENTITY_DOCUMENT, docId,
                "Question: " + truncate(question, 200), categoryId);
    }

    public void logGenerateQA(String userId, String docId, int fineGrainCount, int summaryCount, String categoryId) {
        logAsync(userId, ACTION_GENERATE_QA, ENTITY_DOCUMENT, docId,
                "Generated Q&A: " + fineGrainCount + " fine-grain, " + summaryCount + " summary", categoryId);
    }

    public void logFaqSelect(String userId, String docId, Long qaId, String categoryId) {
        logAsync(userId, ACTION_FAQ_SELECT, ENTITY_DOCUMENT, docId,
                "Q&A ID: " + qaId + " selected for FAQ", categoryId);
    }

    public void logFaqApprove(String userId, String docId, int count, String categoryId) {
        logAsync(userId, ACTION_FAQ_APPROVE, ENTITY_DOCUMENT, docId,
                "Approved " + count + " FAQs", categoryId);
    }

    // Tool actions
    public void logToolUpdate(String userId, String toolId, String details) {
        logAsync(userId, ACTION_TOOL_UPDATE, ENTITY_TOOL, toolId, details, null);
    }

    public void logToolDelete(String userId, String toolId, String toolName) {
        logAsync(userId, ACTION_TOOL_DELETE, ENTITY_TOOL, toolId,
                "Tool: " + toolName, null);
    }

    public void logToolAddToCategory(String userId, String toolId, String categoryId) {
        logAsync(userId, ACTION_TOOL_ADD_TO_CATEGORY, ENTITY_TOOL, toolId,
                "Added to category", categoryId);
    }

    public void logToolRemoveFromCategory(String userId, String toolId, String categoryId) {
        logAsync(userId, ACTION_TOOL_REMOVE_FROM_CATEGORY, ENTITY_TOOL, toolId,
                "Removed from category", categoryId);
    }

    public void logParameterOverride(String userId, String toolId, String parameterPath, String categoryId) {
        logAsync(userId, ACTION_PARAMETER_OVERRIDE, ENTITY_TOOL, toolId,
                "Parameter: " + parameterPath, categoryId);
    }
}
