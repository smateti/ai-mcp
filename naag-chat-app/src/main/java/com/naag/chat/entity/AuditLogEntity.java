package com.naag.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user", columnList = "userId"),
    @Index(name = "idx_audit_session", columnList = "sessionId"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_action", columnList = "action")
})
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    private String sessionId;

    private String messageId;

    @Column(nullable = false)
    private String action; // CHAT_STARTED, MESSAGE_SENT, MESSAGE_RECEIVED, TOOL_INVOKED, ERROR, etc.

    @Column(columnDefinition = "TEXT")
    private String userQuestion;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(columnDefinition = "TEXT")
    private String assistantResponse;

    private String categoryId;
    private String categoryName;

    // Tool/Intent info
    private String intent;
    private String selectedTool;
    private Double confidence;

    // Performance metrics
    private Long processingTimeMs;
    private Integer inputTokens;
    private Integer outputTokens;

    // Status
    @Builder.Default
    private Boolean success = true;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 500)
    private String errorStackTrace;

    // Client info
    private String clientIp;
    private String userAgent;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
