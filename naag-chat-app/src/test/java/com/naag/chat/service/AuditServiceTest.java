package com.naag.chat.service;

import com.naag.chat.config.PersistenceProperties;
import com.naag.chat.config.PersistenceProperties.AuditConfig;
import com.naag.chat.config.PersistenceProperties.StorageType;
import com.naag.chat.entity.AuditLogEntity;
import com.naag.chat.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for AuditService.
 * Tests audit logging for chat operations with H2 storage.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private PersistenceProperties persistenceProperties;

    @Mock
    private AuditLogRepository h2Repository;

    @Mock
    private AuditConfig auditProperties;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        lenient().when(persistenceProperties.getAudit()).thenReturn(auditProperties);
        auditService = new AuditService(persistenceProperties, h2Repository, null);
    }

    @Nested
    @DisplayName("Log Chat Started Tests")
    class LogChatStartedTests {

        @Test
        @DisplayName("Should log chat started to H2")
        void shouldLogChatStartedToH2() {
            // Given
            when(auditProperties.isEnabled()).thenReturn(true);
            when(auditProperties.getType()).thenReturn(StorageType.H2);

            // When
            auditService.logChatStarted("user-1", "session-1", "cat-1", "Test Category", "127.0.0.1", "Mozilla/5.0");

            // Then
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(h2Repository).save(captor.capture());

            AuditLogEntity saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo("user-1");
            assertThat(saved.getSessionId()).isEqualTo("session-1");
            assertThat(saved.getAction()).isEqualTo("CHAT_STARTED");
            assertThat(saved.getCategoryId()).isEqualTo("cat-1");
            assertThat(saved.getSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should not log when audit is disabled")
        void shouldNotLogWhenAuditDisabled() {
            // Given
            when(auditProperties.isEnabled()).thenReturn(false);

            // When
            auditService.logChatStarted("user-1", "session-1", "cat-1", "Test", "127.0.0.1", "Mozilla");

            // Then
            verifyNoInteractions(h2Repository);
        }
    }

    @Nested
    @DisplayName("Log Message Processed Tests")
    class LogMessageProcessedTests {

        @Test
        @DisplayName("Should log message processed with prompts")
        void shouldLogMessageProcessedWithPrompts() {
            // Given
            when(auditProperties.isEnabled()).thenReturn(true);
            when(auditProperties.getType()).thenReturn(StorageType.H2);
            when(auditProperties.isLogPrompts()).thenReturn(true);
            when(auditProperties.isLogResponses()).thenReturn(true);

            // When
            auditService.logMessageProcessed(
                    "user-1", "session-1", "msg-1",
                    "How do I reset my password?",
                    "Go to settings and click reset",
                    "cat-1", "Support",
                    "password_reset", "faq_tool", 0.95,
                    150L, true, null,
                    "127.0.0.1", "Mozilla/5.0"
            );

            // Then
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(h2Repository).save(captor.capture());

            AuditLogEntity saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo("MESSAGE_PROCESSED");
            assertThat(saved.getUserQuestion()).isEqualTo("How do I reset my password?");
            assertThat(saved.getAssistantResponse()).isEqualTo("Go to settings and click reset");
            assertThat(saved.getIntent()).isEqualTo("password_reset");
            assertThat(saved.getSelectedTool()).isEqualTo("faq_tool");
            assertThat(saved.getProcessingTimeMs()).isEqualTo(150L);
            assertThat(saved.getSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should redact prompts when logging is disabled")
        void shouldRedactPromptsWhenLoggingDisabled() {
            // Given
            when(auditProperties.isEnabled()).thenReturn(true);
            when(auditProperties.getType()).thenReturn(StorageType.H2);
            when(auditProperties.isLogPrompts()).thenReturn(false);
            when(auditProperties.isLogResponses()).thenReturn(false);

            // When
            auditService.logMessageProcessed(
                    "user-1", "session-1", "msg-1",
                    "Sensitive question",
                    "Sensitive response",
                    "cat-1", "Support",
                    "intent", "tool", 0.9,
                    100L, true, null,
                    "127.0.0.1", "Mozilla"
            );

            // Then
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(h2Repository).save(captor.capture());

            AuditLogEntity saved = captor.getValue();
            assertThat(saved.getUserQuestion()).isEqualTo("[REDACTED]");
            assertThat(saved.getAssistantResponse()).isEqualTo("[REDACTED]");
        }
    }

    @Nested
    @DisplayName("Log Error Tests")
    class LogErrorTests {

        @Test
        @DisplayName("Should log error with stack trace truncation")
        void shouldLogErrorWithStackTraceTruncation() {
            // Given
            when(auditProperties.isEnabled()).thenReturn(true);
            when(auditProperties.getType()).thenReturn(StorageType.H2);

            String longStackTrace = "x".repeat(1000);

            // When
            auditService.logError(
                    "user-1", "session-1", "PROCESSING_ERROR",
                    "Something went wrong", longStackTrace,
                    "127.0.0.1", "Mozilla"
            );

            // Then
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(h2Repository).save(captor.capture());

            AuditLogEntity saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo("PROCESSING_ERROR");
            assertThat(saved.getSuccess()).isFalse();
            assertThat(saved.getErrorMessage()).isEqualTo("Something went wrong");
            assertThat(saved.getErrorStackTrace()).hasSize(500); // Truncated
        }
    }

    @Nested
    @DisplayName("Query Audit Logs Tests")
    class QueryAuditLogsTests {

        @Test
        @DisplayName("Should get audit logs for user")
        void shouldGetAuditLogsForUser() {
            // Given
            when(auditProperties.getType()).thenReturn(StorageType.H2);

            AuditLogEntity log1 = AuditLogEntity.builder()
                    .id("log-1")
                    .userId("user-1")
                    .action("CHAT_STARTED")
                    .build();

            when(h2Repository.findByUserIdOrderByTimestampDesc(eq("user-1"), any()))
                    .thenReturn(new PageImpl<>(List.of(log1)));

            // When
            List<AuditLogEntity> logs = auditService.getAuditLogs("user-1", 0, 10);

            // Then
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getUserId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("Should get session audit logs")
        void shouldGetSessionAuditLogs() {
            // Given
            when(auditProperties.getType()).thenReturn(StorageType.H2);

            AuditLogEntity log1 = AuditLogEntity.builder()
                    .id("log-1")
                    .sessionId("session-1")
                    .action("CHAT_STARTED")
                    .build();

            when(h2Repository.findBySessionIdOrderByTimestampAsc("session-1"))
                    .thenReturn(List.of(log1));

            // When
            List<AuditLogEntity> logs = auditService.getSessionAuditLogs("session-1");

            // Then
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getSessionId()).isEqualTo("session-1");
        }

        @Test
        @DisplayName("Should get failed operations")
        void shouldGetFailedOperations() {
            // Given
            when(auditProperties.getType()).thenReturn(StorageType.H2);

            AuditLogEntity errorLog = AuditLogEntity.builder()
                    .id("log-1")
                    .userId("user-1")
                    .action("ERROR")
                    .success(false)
                    .errorMessage("Test error")
                    .build();

            when(h2Repository.findFailedByUserId("user-1"))
                    .thenReturn(List.of(errorLog));

            // When
            List<AuditLogEntity> logs = auditService.getFailedOperations("user-1");

            // Then
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("User Stats Tests")
    class UserStatsTests {

        @Test
        @DisplayName("Should get user stats")
        void shouldGetUserStats() {
            // Given
            when(h2Repository.getAverageProcessingTime("user-1")).thenReturn(150.0);
            when(h2Repository.countByUserIdAndAction("user-1", "MESSAGE_PROCESSED")).thenReturn(100L);
            when(h2Repository.getToolUsageStats("user-1")).thenReturn(List.of(
                    new Object[]{"faq_tool", 50L},
                    new Object[]{"rag_tool", 30L}
            ));

            // When
            var stats = auditService.getUserStats("user-1");

            // Then
            assertThat(stats.get("averageProcessingTimeMs")).isEqualTo(150.0);
            assertThat(stats.get("totalMessages")).isEqualTo(100L);
            assertThat(stats.get("toolUsage")).isInstanceOf(java.util.Map.class);
        }
    }
}
