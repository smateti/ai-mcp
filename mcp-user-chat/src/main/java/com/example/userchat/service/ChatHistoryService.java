package com.example.userchat.service;

import com.example.userchat.entity.ChatMessage;
import com.example.userchat.entity.ChatSession;
import com.example.userchat.llm.OpenAIChatClient;
import com.example.userchat.repository.ChatMessageRepository;
import com.example.userchat.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final OpenAIChatClient llmClient;

    public ChatHistoryService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            OpenAIChatClient llmClient) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.llmClient = llmClient;
    }

    @Transactional
    public ChatSession createSession(String categoryId, String categoryName, String firstQuestion) {
        ChatSession session = new ChatSession(categoryId, categoryName, firstQuestion);

        // Generate chat name using LLM
        try {
            String generatedName = llmClient.generateChatName(firstQuestion);
            session.setName(generatedName.trim());
            log.info("Generated chat name: {}", generatedName);
        } catch (Exception e) {
            log.warn("Failed to generate chat name, using default", e);
            session.setName("Chat about " + categoryName);
        }

        return sessionRepository.save(session);
    }

    public Optional<ChatSession> getSession(String sessionId) {
        return sessionRepository.findById(sessionId);
    }

    public List<ChatSession> getAllSessions() {
        return sessionRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<ChatSession> getSessionsByCategory(String categoryId) {
        return sessionRepository.findByCategoryIdOrderByUpdatedAtDesc(categoryId);
    }

    @Transactional
    public void saveMessage(String sessionId, String role, String content, String toolUsed) {
        ChatMessage message = new ChatMessage(sessionId, role, content, toolUsed);
        messageRepository.save(message);

        // Update session message count and timestamp
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setMessageCount(session.getMessageCount() + 1);
            sessionRepository.save(session);
        });
    }

    public List<ChatMessage> getMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
    }

    public long getSessionCount() {
        return sessionRepository.count();
    }

    public long getSessionCountByCategory(String categoryId) {
        return sessionRepository.countByCategoryId(categoryId);
    }
}
