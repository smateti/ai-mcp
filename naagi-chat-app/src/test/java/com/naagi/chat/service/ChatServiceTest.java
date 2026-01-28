package com.naagi.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naagi.chat.entity.ChatSessionEntity;
import com.naagi.chat.metrics.ChatMetrics;
import com.naagi.chat.model.ChatMessage;
import com.naagi.chat.model.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatService.
 * Tests core chat functionality including session management and message processing.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private OrchestratorClient orchestratorClient;

    @Mock
    private ChatHistoryService historyService;

    @Mock
    private AuditService auditService;

    @Mock
    private ChatMetrics metrics;

    private ChatService chatService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(orchestratorClient, historyService, auditService, metrics);
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Session Management Tests")
    class SessionManagementTests {

        @Test
        @DisplayName("Should create new session when not found")
        void shouldCreateNewSessionWhenNotFound() {
            // Given
            String sessionId = "session-1";
            when(historyService.getSession(sessionId)).thenReturn(Optional.empty());

            // When
            ChatSession session = chatService.getOrCreateSession(sessionId);

            // Then
            assertThat(session).isNotNull();
            assertThat(session.getSessionId()).isEqualTo(sessionId);
            verify(historyService).createSession(sessionId, "anonymous", null, null);
            verify(metrics).recordSessionCreated();
        }

        @Test
        @DisplayName("Should return existing session from database")
        void shouldReturnExistingSessionFromDatabase() {
            // Given
            String sessionId = "session-1";
            ChatSessionEntity entity = ChatSessionEntity.builder()
                    .id(sessionId)
                    .userId("user-1")
                    .createdAt(LocalDateTime.now())
                    .build();
            ChatSession existingSession = new ChatSession();
            existingSession.setSessionId(sessionId);

            when(historyService.getSession(sessionId)).thenReturn(Optional.of(entity));
            when(historyService.toDto(entity)).thenReturn(existingSession);

            // When
            ChatSession session = chatService.getOrCreateSession(sessionId);

            // Then
            assertThat(session).isNotNull();
            assertThat(session.getSessionId()).isEqualTo(sessionId);
            verify(historyService, never()).createSession(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should cache session after creation")
        void shouldCacheSessionAfterCreation() {
            // Given
            String sessionId = "session-1";
            when(historyService.getSession(sessionId)).thenReturn(Optional.empty());

            // When - first call creates session
            ChatSession session1 = chatService.getOrCreateSession(sessionId);

            // Then - second call should return cached session (no DB call)
            ChatSession session2 = chatService.getOrCreateSession(sessionId);

            assertThat(session1).isSameAs(session2);
            verify(historyService, times(1)).createSession(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should delete session and invalidate cache")
        void shouldDeleteSessionAndInvalidateCache() {
            // Given
            String sessionId = "session-1";
            when(historyService.getSession(sessionId)).thenReturn(Optional.empty());
            chatService.getOrCreateSession(sessionId);

            // When
            chatService.deleteSession(sessionId);

            // Then
            verify(historyService).deleteSession(sessionId);
            verify(metrics).recordSessionDeleted();
        }
    }

    @Nested
    @DisplayName("Message Processing Tests")
    class MessageProcessingTests {

        @Test
        @DisplayName("Should process message successfully")
        void shouldProcessMessageSuccessfully() throws Exception {
            // Given
            String sessionId = "session-1";
            String userMessage = "How do I reset my password?";

            when(historyService.getSession(sessionId)).thenReturn(Optional.empty());

            ObjectNode orchestratorResponse = objectMapper.createObjectNode();
            orchestratorResponse.put("response", "Go to settings and click reset");
            orchestratorResponse.put("intent", "password_reset");
            orchestratorResponse.put("selectedTool", "faq_tool");
            orchestratorResponse.put("confidence", 0.95);

            when(orchestratorClient.orchestrate(eq(userMessage), eq(sessionId), isNull()))
                    .thenReturn(orchestratorResponse);

            // When
            ChatMessage result = chatService.processMessage(sessionId, userMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getRole()).isEqualTo("assistant");
            assertThat(result.getContent()).isEqualTo("Go to settings and click reset");
            assertThat(result.getMetadata()).containsEntry("success", true);

            verify(historyService, times(2)).addMessage(eq(sessionId), anyString(), anyString(), anyMap());
            verify(auditService).logMessageProcessed(
                    eq("anonymous"), eq(sessionId), anyString(),
                    eq(userMessage), eq("Go to settings and click reset"),
                    isNull(), isNull(),
                    eq("password_reset"), eq("faq_tool"), eq(0.95),
                    anyLong(), eq(true), isNull(),
                    isNull(), isNull()
            );
            verify(metrics).recordMessageProcessingTime(anyLong());
            verify(metrics).recordOrchestratorCallTime(anyLong());
        }

        @Test
        @DisplayName("Should handle orchestrator error gracefully")
        void shouldHandleOrchestratorErrorGracefully() {
            // Given
            String sessionId = "session-1";
            String userMessage = "Hello";

            when(historyService.getSession(sessionId)).thenReturn(Optional.empty());
            when(orchestratorClient.orchestrate(any(), any(), any()))
                    .thenThrow(new RuntimeException("Connection timeout"));

            // When
            ChatMessage result = chatService.processMessage(sessionId, userMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).contains("error processing your request");
            assertThat(result.getMetadata()).containsEntry("success", false);

            verify(auditService).logError(
                    eq("anonymous"), eq(sessionId), eq("ORCHESTRATOR_ERROR"),
                    eq("Connection timeout"), anyString(),
                    isNull(), isNull()
            );
            verify(metrics).recordMessageError();
        }

        @Test
        @DisplayName("Should include processing time in metadata")
        void shouldIncludeProcessingTimeInMetadata() throws Exception {
            // Given
            String sessionId = "session-1";
            when(historyService.getSession(sessionId)).thenReturn(Optional.empty());

            ObjectNode response = objectMapper.createObjectNode();
            response.put("response", "Test response");
            when(orchestratorClient.orchestrate(any(), any(), any())).thenReturn(response);

            // When
            ChatMessage result = chatService.processMessage(sessionId, "Test");

            // Then
            assertThat(result.getMetadata()).containsKey("processingTimeMs");
            assertThat((Long) result.getMetadata().get("processingTimeMs")).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Should process message with full context")
        void shouldProcessMessageWithFullContext() throws Exception {
            // Given
            String sessionId = "session-1";
            String userId = "user-123";
            String categoryId = "cat-1";
            String categoryName = "Support";
            String clientIp = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            when(historyService.getSession(sessionId)).thenReturn(Optional.empty());

            ObjectNode response = objectMapper.createObjectNode();
            response.put("response", "Help response");
            when(orchestratorClient.orchestrate(any(), any(), any())).thenReturn(response);

            // When
            ChatMessage result = chatService.processMessage(
                    sessionId, "Help me", userId, categoryId, categoryName, clientIp, userAgent);

            // Then
            assertThat(result).isNotNull();
            verify(historyService).createSession(sessionId, userId, categoryId, categoryName);
            verify(auditService).logMessageProcessed(
                    eq(userId), eq(sessionId), anyString(),
                    eq("Help me"), eq("Help response"),
                    eq(categoryId), eq(categoryName),
                    any(), any(), any(),
                    anyLong(), eq(true), isNull(),
                    eq(clientIp), eq(userAgent)
            );
        }
    }

    @Nested
    @DisplayName("Session History Tests")
    class SessionHistoryTests {

        @Test
        @DisplayName("Should get user sessions")
        void shouldGetUserSessions() {
            // Given
            String userId = "user-1";
            ChatSessionEntity entity = ChatSessionEntity.builder()
                    .id("session-1")
                    .userId(userId)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(historyService.getUserSessions(userId)).thenReturn(List.of(entity));

            // When
            List<ChatSession> sessions = chatService.getUserSessions(userId);

            // Then
            assertThat(sessions).hasSize(1);
            assertThat(sessions.get(0).getSessionId()).isEqualTo("session-1");
        }

        @Test
        @DisplayName("Should get recent sessions")
        void shouldGetRecentSessions() {
            // Given
            String userId = "user-1";
            ChatSessionEntity entity = ChatSessionEntity.builder()
                    .id("session-1")
                    .userId(userId)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(historyService.getRecentSessions(userId, 7)).thenReturn(List.of(entity));

            // When
            List<ChatSession> sessions = chatService.getRecentSessions(userId, 7);

            // Then
            assertThat(sessions).hasSize(1);
            verify(historyService).getRecentSessions(userId, 7);
        }

        @Test
        @DisplayName("Should get session messages")
        void shouldGetSessionMessages() {
            // Given
            String sessionId = "session-1";
            var messageEntity = mock(com.naagi.chat.entity.ChatMessageEntity.class);
            ChatMessage message = new ChatMessage("msg-1", "user", "Hello", LocalDateTime.now(), Map.of());

            when(historyService.getSessionMessages(sessionId)).thenReturn(List.of(messageEntity));
            when(historyService.toDto(messageEntity)).thenReturn(message);

            // When
            List<ChatMessage> messages = chatService.getSessionMessages(sessionId);

            // Then
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getContent()).isEqualTo("Hello");
        }
    }

    @Nested
    @DisplayName("User Stats Tests")
    class UserStatsTests {

        @Test
        @DisplayName("Should get user statistics")
        void shouldGetUserStatistics() {
            // Given
            String userId = "user-1";
            when(historyService.getSessionCount(userId)).thenReturn(5L);
            when(historyService.getMessageCount(userId)).thenReturn(50L);
            when(auditService.getUserStats(userId)).thenReturn(Map.of("avgProcessingTime", 150.0));

            // When
            Map<String, Object> stats = chatService.getUserStats(userId);

            // Then
            assertThat(stats).containsEntry("sessionCount", 5L);
            assertThat(stats).containsEntry("messageCount", 50L);
            assertThat(stats).containsKey("auditStats");
        }
    }
}
