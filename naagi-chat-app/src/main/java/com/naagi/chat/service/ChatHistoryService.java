package com.naagi.chat.service;

import com.naagi.chat.config.PersistenceProperties;
import com.naagi.chat.entity.ChatMessageEntity;
import com.naagi.chat.entity.ChatSessionEntity;
import com.naagi.chat.model.ChatMessage;
import com.naagi.chat.model.ChatSession;
import com.naagi.chat.repository.ChatMessageRepository;
import com.naagi.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatHistoryService {

    private final PersistenceProperties properties;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    @Transactional
    public ChatSessionEntity createSession(String sessionId, String userId, String categoryId, String categoryName) {
        if (!properties.getHistory().isEnabled()) {
            return null;
        }

        ChatSessionEntity session = ChatSessionEntity.builder()
                .id(sessionId)
                .userId(userId)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .title("New Chat")
                .createdAt(LocalDateTime.now())
                .active(true)
                .build();

        ChatSessionEntity saved = sessionRepository.save(session);
        log.debug("Created session: {} for user: {}", sessionId, userId);
        return saved;
    }

    @Transactional
    public ChatSessionEntity getOrCreateSession(String sessionId, String userId, String categoryId, String categoryName) {
        if (!properties.getHistory().isEnabled()) {
            return null;
        }

        return sessionRepository.findById(sessionId)
                .orElseGet(() -> createSession(sessionId, userId, categoryId, categoryName));
    }

    @Transactional
    public ChatMessageEntity addMessage(String sessionId, String role, String content, Map<String, Object> metadata) {
        if (!properties.getHistory().isEnabled()) {
            return null;
        }

        Optional<ChatSessionEntity> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Session not found for message: {}", sessionId);
            return null;
        }

        ChatSessionEntity session = sessionOpt.get();

        ChatMessageEntity message = ChatMessageEntity.builder()
                .session(session)
                .role(role)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();

        // Extract metadata
        if (metadata != null) {
            message.setIntent((String) metadata.get("intent"));
            message.setSelectedTool((String) metadata.get("selectedTool"));
            if (metadata.get("confidence") instanceof Number) {
                message.setConfidence(((Number) metadata.get("confidence")).doubleValue());
            }
            message.setRequiresConfirmation((Boolean) metadata.get("requiresConfirmation"));
            if (metadata.get("processingTimeMs") instanceof Number) {
                message.setProcessingTimeMs(((Number) metadata.get("processingTimeMs")).longValue());
            }
            message.setSuccess((Boolean) metadata.getOrDefault("success", true));
            message.setErrorMessage((String) metadata.get("errorMessage"));
        }

        // Save message directly (avoid collection issues with orphan removal)
        ChatMessageEntity savedMessage = messageRepository.save(message);

        // Update session metadata
        session.setMessageCount(session.getMessageCount() + 1);
        session.setLastMessageAt(LocalDateTime.now());

        // Update session title from first user message
        if ("user".equals(role) && session.getMessageCount() == 1) {
            String title = content.length() > 50 ? content.substring(0, 47) + "..." : content;
            session.setTitle(title);
        }

        sessionRepository.save(session);
        log.debug("Added {} message to session: {}", role, sessionId);

        return savedMessage;
    }

    public Optional<ChatSessionEntity> getSession(String sessionId) {
        if (!properties.getHistory().isEnabled()) {
            return Optional.empty();
        }
        return sessionRepository.findById(sessionId);
    }

    @Transactional
    public void updateSessionTitle(String sessionId, String firstMessage) {
        if (!properties.getHistory().isEnabled()) {
            return;
        }
        sessionRepository.findById(sessionId).ifPresent(session -> {
            // Only update if still "New Chat" (not already set)
            if ("New Chat".equals(session.getTitle())) {
                String title = firstMessage.length() > 50 ? firstMessage.substring(0, 47) + "..." : firstMessage;
                session.setTitle(title);
                sessionRepository.save(session);
                log.debug("Updated session title to: {}", title);
            }
        });
    }

    public List<ChatSessionEntity> getUserSessions(String userId) {
        if (!properties.getHistory().isEnabled()) {
            return List.of();
        }
        return sessionRepository.findByUserIdOrderByLastMessageAtDesc(userId);
    }

    public List<ChatSessionEntity> getActiveSessions(String userId) {
        if (!properties.getHistory().isEnabled()) {
            return List.of();
        }
        return sessionRepository.findByUserIdAndActiveTrue(userId);
    }

    public List<ChatSessionEntity> getSessionsByCategory(String userId, String categoryId) {
        if (!properties.getHistory().isEnabled()) {
            return List.of();
        }
        return sessionRepository.findByUserIdAndCategoryIdOrderByLastMessageAtDesc(userId, categoryId);
    }

    public List<ChatSessionEntity> getRecentSessions(String userId, int days) {
        if (!properties.getHistory().isEnabled()) {
            return List.of();
        }
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return sessionRepository.findRecentSessions(userId, since);
    }

    public List<ChatMessageEntity> getSessionMessages(String sessionId) {
        if (!properties.getHistory().isEnabled()) {
            return List.of();
        }
        return messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    public List<ChatSessionEntity> searchSessions(String userId, String query) {
        if (!properties.getHistory().isEnabled()) {
            return List.of();
        }
        return sessionRepository.searchByTitle(userId, query);
    }

    public List<ChatMessageEntity> searchMessages(String userId, String query) {
        if (!properties.getHistory().isEnabled()) {
            return List.of();
        }
        return messageRepository.searchByContent(userId, query);
    }

    @Transactional
    public void deleteSession(String sessionId) {
        if (!properties.getHistory().isEnabled()) {
            return;
        }
        sessionRepository.deleteById(sessionId);
        log.info("Deleted session: {}", sessionId);
    }

    @Transactional
    public void archiveSession(String sessionId) {
        if (!properties.getHistory().isEnabled()) {
            return;
        }
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setActive(false);
            sessionRepository.save(session);
            log.info("Archived session: {}", sessionId);
        });
    }

    // Convert to DTO model for API responses
    public ChatSession toDto(ChatSessionEntity entity) {
        ChatSession session = new ChatSession();
        session.setSessionId(entity.getId());
        session.setCreatedAt(entity.getCreatedAt());
        session.setLastMessageAt(entity.getLastMessageAt());

        List<ChatMessage> messages = entity.getMessages().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        session.setMessages(messages);

        return session;
    }

    public ChatMessage toDto(ChatMessageEntity entity) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        if (entity.getIntent() != null) metadata.put("intent", entity.getIntent());
        if (entity.getSelectedTool() != null) metadata.put("selectedTool", entity.getSelectedTool());
        if (entity.getConfidence() != null) metadata.put("confidence", entity.getConfidence());
        if (entity.getRequiresConfirmation() != null) metadata.put("requiresConfirmation", entity.getRequiresConfirmation());
        if (entity.getProcessingTimeMs() != null) metadata.put("processingTimeMs", entity.getProcessingTimeMs());

        return new ChatMessage(
                entity.getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getTimestamp(),
                metadata.isEmpty() ? null : metadata
        );
    }

    public long getSessionCount(String userId) {
        return sessionRepository.countByUserId(userId);
    }

    public long getMessageCount(String userId) {
        return messageRepository.countMessagesByUserId(userId);
    }
}
