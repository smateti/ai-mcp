package com.naagi.categoryadmin.service;

import com.naagi.categoryadmin.elasticsearch.AuditLogElasticsearchService;
import com.naagi.categoryadmin.model.AuditLog;
import com.naagi.categoryadmin.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditLogService.
 * Tests audit logging for admin operations.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogElasticsearchService elasticsearchService;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, elasticsearchService);
    }

    @Nested
    @DisplayName("Log Operations Tests")
    class LogOperationsTests {

        @Test
        @DisplayName("Should log action successfully")
        void shouldLogActionSuccessfully() {
            // Given
            AuditLog savedLog = AuditLog.builder()
                    .id(1L)
                    .userId("user-1")
                    .action("CATEGORY_CREATE")
                    .build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.log("user-1", AuditLogService.ACTION_CATEGORY_CREATE,
                    AuditLogService.ENTITY_CATEGORY, "cat-1",
                    "Category: Test", "cat-1",
                    AuditLog.AuditStatus.SUCCESS, null);

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog captured = captor.getValue();
            assertThat(captured.getUserId()).isEqualTo("user-1");
            assertThat(captured.getAction()).isEqualTo("CATEGORY_CREATE");
            assertThat(captured.getEntityType()).isEqualTo("CATEGORY");
            assertThat(captured.getEntityId()).isEqualTo("cat-1");
            assertThat(captured.getStatus()).isEqualTo(AuditLog.AuditStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should log failure with error message")
        void shouldLogFailureWithErrorMessage() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.log("user-1", AuditLogService.ACTION_TOOL_CALL,
                    AuditLogService.ENTITY_TOOL, "tool-1",
                    "Parameters: {}", "cat-1",
                    AuditLog.AuditStatus.FAILURE, "Connection timeout");

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog captured = captor.getValue();
            assertThat(captured.getStatus()).isEqualTo(AuditLog.AuditStatus.FAILURE);
            assertThat(captured.getErrorMessage()).isEqualTo("Connection timeout");
        }

        @Test
        @DisplayName("Should truncate long details")
        void shouldTruncateLongDetails() {
            // Given
            String longDetails = "x".repeat(3000);
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.log("user-1", "ACTION", "ENTITY", "id-1",
                    longDetails, null, AuditLog.AuditStatus.SUCCESS, null);

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertThat(captor.getValue().getDetails()).hasSize(2000);
        }

        @Test
        @DisplayName("Should sync to Elasticsearch when enabled")
        void shouldSyncToElasticsearchWhenEnabled() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.log("user-1", "ACTION", "ENTITY", "id-1",
                    "details", null, AuditLog.AuditStatus.SUCCESS, null);

            // Then
            verify(elasticsearchService).indexAuditLog(savedLog);
        }

        @Test
        @DisplayName("Should handle null userId")
        void shouldHandleNullUserId() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.log(null, "ACTION", "ENTITY", "id-1",
                    "details", null, AuditLog.AuditStatus.SUCCESS, null);

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertThat(captor.getValue().getUserId()).isEqualTo("anonymous");
        }
    }

    @Nested
    @DisplayName("Convenience Method Tests")
    class ConvenienceMethodTests {

        @Test
        @DisplayName("Should log chat message")
        void shouldLogChatMessage() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.logChat("user-1", "session-1", "Hello, how can you help?", "cat-1");

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog captured = captor.getValue();
            assertThat(captured.getAction()).isEqualTo("CHAT");
            assertThat(captured.getEntityType()).isEqualTo("CHAT");
            assertThat(captured.getEntityId()).isEqualTo("session-1");
            assertThat(captured.getDetails()).contains("Hello, how can you help?");
        }

        @Test
        @DisplayName("Should log tool call success")
        void shouldLogToolCallSuccess() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.logToolCall("user-1", "tool-1", "{\"param\": \"value\"}", "cat-1", true, null);

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog captured = captor.getValue();
            assertThat(captured.getAction()).isEqualTo("TOOL_CALL");
            assertThat(captured.getStatus()).isEqualTo(AuditLog.AuditStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should log tool call failure")
        void shouldLogToolCallFailure() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.logToolCall("user-1", "tool-1", "{}", "cat-1", false, "Tool not found");

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog captured = captor.getValue();
            assertThat(captured.getStatus()).isEqualTo(AuditLog.AuditStatus.FAILURE);
            assertThat(captured.getErrorMessage()).isEqualTo("Tool not found");
        }

        @Test
        @DisplayName("Should log document upload")
        void shouldLogDocumentUpload() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.logDocumentUpload("user-1", "doc-1", "FAQ Document", "cat-1");

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog captured = captor.getValue();
            assertThat(captured.getAction()).isEqualTo("DOCUMENT_UPLOAD");
            assertThat(captured.getEntityType()).isEqualTo("DOCUMENT");
            assertThat(captured.getDetails()).contains("FAQ Document");
        }

        @Test
        @DisplayName("Should log category operations")
        void shouldLogCategoryOperations() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.logCategoryCreate("user-1", "cat-1", "Support");
            auditLogService.logCategoryUpdate("user-1", "cat-1", "Support Updated");
            auditLogService.logCategoryDelete("user-1", "cat-1", "Support");

            // Then
            verify(auditLogRepository, times(3)).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("Should log OpenAPI import")
        void shouldLogOpenApiImport() {
            // Given
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            auditLogService.logOpenApiImport("user-1", "api.yaml", 15, "cat-1");

            // Then
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog captured = captor.getValue();
            assertThat(captured.getAction()).isEqualTo("OPENAPI_IMPORT");
            assertThat(captured.getDetails()).contains("15 tools");
            assertThat(captured.getDetails()).contains("api.yaml");
        }
    }

    @Nested
    @DisplayName("Query Methods Tests")
    class QueryMethodsTests {

        @Test
        @DisplayName("Should get audit logs with pagination")
        void shouldGetAuditLogsWithPagination() {
            // Given
            AuditLog log1 = AuditLog.builder().id(1L).action("ACTION1").build();
            Page<AuditLog> page = new PageImpl<>(List.of(log1));
            when(auditLogRepository.findAllByOrderByTimestampDesc(any(PageRequest.class))).thenReturn(page);

            // When
            Page<AuditLog> result = auditLogService.getAuditLogs(0, 10);

            // Then
            assertThat(result.getContent()).hasSize(1);
            verify(auditLogRepository).findAllByOrderByTimestampDesc(PageRequest.of(0, 10));
        }

        @Test
        @DisplayName("Should get audit logs by user")
        void shouldGetAuditLogsByUser() {
            // Given
            AuditLog log1 = AuditLog.builder().id(1L).userId("user-1").build();
            Page<AuditLog> page = new PageImpl<>(List.of(log1));
            when(auditLogRepository.findByUserIdOrderByTimestampDesc(eq("user-1"), any())).thenReturn(page);

            // When
            Page<AuditLog> result = auditLogService.getAuditLogsByUser("user-1", 0, 10);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("Should get audit logs by action")
        void shouldGetAuditLogsByAction() {
            // Given
            AuditLog log1 = AuditLog.builder().id(1L).action("CHAT").build();
            Page<AuditLog> page = new PageImpl<>(List.of(log1));
            when(auditLogRepository.findByActionOrderByTimestampDesc(eq("CHAT"), any())).thenReturn(page);

            // When
            Page<AuditLog> result = auditLogService.getAuditLogsByAction("CHAT", 0, 10);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAction()).isEqualTo("CHAT");
        }

        @Test
        @DisplayName("Should get audit logs by entity")
        void shouldGetAuditLogsByEntity() {
            // Given
            AuditLog log1 = AuditLog.builder().id(1L).entityType("DOCUMENT").entityId("doc-1").build();
            Page<AuditLog> page = new PageImpl<>(List.of(log1));
            when(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(
                    eq("DOCUMENT"), eq("doc-1"), any())).thenReturn(page);

            // When
            Page<AuditLog> result = auditLogService.getAuditLogsByEntity("DOCUMENT", "doc-1", 0, 10);

            // Then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should get audit logs by category")
        void shouldGetAuditLogsByCategory() {
            // Given
            AuditLog log1 = AuditLog.builder().id(1L).categoryId("cat-1").build();
            Page<AuditLog> page = new PageImpl<>(List.of(log1));
            when(auditLogRepository.findByCategoryIdOrderByTimestampDesc(eq("cat-1"), any())).thenReturn(page);

            // When
            Page<AuditLog> result = auditLogService.getAuditLogsByCategory("cat-1", 0, 10);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCategoryId()).isEqualTo("cat-1");
        }

        @Test
        @DisplayName("Should search audit logs with filters")
        void shouldSearchAuditLogsWithFilters() {
            // Given
            LocalDateTime start = LocalDateTime.now().minusDays(7);
            LocalDateTime end = LocalDateTime.now();
            AuditLog log1 = AuditLog.builder().id(1L).build();
            Page<AuditLog> page = new PageImpl<>(List.of(log1));
            when(auditLogRepository.findByFilters(any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

            // When
            Page<AuditLog> result = auditLogService.searchAuditLogs(
                    "user-1", "CHAT", "CHAT", "cat-1", start, end, 0, 10);

            // Then
            assertThat(result.getContent()).hasSize(1);
            verify(auditLogRepository).findByFilters(
                    eq("user-1"), eq("CHAT"), eq("CHAT"), eq("cat-1"),
                    eq(start), eq(end), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should get distinct values")
        void shouldGetDistinctValues() {
            // Given
            when(auditLogRepository.findDistinctUserIds()).thenReturn(List.of("user-1", "user-2"));
            when(auditLogRepository.findDistinctActions()).thenReturn(List.of("CHAT", "LOGIN"));
            when(auditLogRepository.findDistinctEntityTypes()).thenReturn(List.of("CHAT", "USER"));

            // When
            List<String> userIds = auditLogService.getDistinctUserIds();
            List<String> actions = auditLogService.getDistinctActions();
            List<String> entityTypes = auditLogService.getDistinctEntityTypes();

            // Then
            assertThat(userIds).containsExactly("user-1", "user-2");
            assertThat(actions).containsExactly("CHAT", "LOGIN");
            assertThat(entityTypes).containsExactly("CHAT", "USER");
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get statistics for time period")
        void shouldGetStatisticsForTimePeriod() {
            // Given
            when(auditLogRepository.countSince(any())).thenReturn(100L);
            when(auditLogRepository.countFailuresSince(any())).thenReturn(5L);
            when(auditLogRepository.countByActionSince(any())).thenReturn(List.of(
                    new Object[]{"CHAT", 50L},
                    new Object[]{"LOGIN", 30L}
            ));
            when(auditLogRepository.countByUserSince(any())).thenReturn(List.of(
                    new Object[]{"user-1", 60L},
                    new Object[]{"user-2", 40L}
            ));

            // When
            Map<String, Object> stats = auditLogService.getStatistics(24);

            // Then
            assertThat(stats.get("totalLogs")).isEqualTo(100L);
            assertThat(stats.get("failures")).isEqualTo(5L);
            assertThat(stats.get("periodHours")).isEqualTo(24);

            @SuppressWarnings("unchecked")
            Map<String, Long> actionBreakdown = (Map<String, Long>) stats.get("actionBreakdown");
            assertThat(actionBreakdown).containsEntry("CHAT", 50L);
            assertThat(actionBreakdown).containsEntry("LOGIN", 30L);

            @SuppressWarnings("unchecked")
            Map<String, Long> userActivity = (Map<String, Long>) stats.get("userActivity");
            assertThat(userActivity).containsEntry("user-1", 60L);
            assertThat(userActivity).containsEntry("user-2", 40L);
        }
    }

    @Nested
    @DisplayName("Elasticsearch Disabled Tests")
    class ElasticsearchDisabledTests {

        @Test
        @DisplayName("Should work without Elasticsearch")
        void shouldWorkWithoutElasticsearch() {
            // Given - create service without Elasticsearch
            AuditLogService serviceWithoutEs = new AuditLogService(auditLogRepository, null);
            AuditLog savedLog = AuditLog.builder().id(1L).build();
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

            // When
            serviceWithoutEs.log("user-1", "ACTION", "ENTITY", "id-1",
                    "details", null, AuditLog.AuditStatus.SUCCESS, null);

            // Then - should not throw, should save to repository
            verify(auditLogRepository).save(any(AuditLog.class));
            verifyNoInteractions(elasticsearchService);
        }
    }
}
