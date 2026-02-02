package com.naagi.llm.service;

import com.naagi.llm.entity.Conversation;
import com.naagi.llm.entity.ConversationTurn;
import com.naagi.llm.model.*;
import com.naagi.llm.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";

    private final ConversationRepository conversationRepository;
    private final LlamaCppProxy llamaCppProxy;

    @Transactional
    public ConversationResponse chat(ConversationRequest request) {
        Conversation conversation = resolveConversation(request);

        // Add user message
        conversation.addTurn("user", request.message());

        // Build full message history for LLM
        List<ChatMessage> messages = buildMessages(conversation);

        double temperature = request.temperature() != null ? request.temperature() : 0.7;
        int maxTokens = request.maxTokens() != null ? request.maxTokens() : 512;

        ChatRequest chatRequest = ChatRequest.of(messages, temperature, maxTokens);
        ChatResponse response = llamaCppProxy.chat(chatRequest);

        String assistantMessage = response.content() != null ? response.content() : "";

        // Add assistant response
        conversation.addTurn("assistant", assistantMessage);
        conversationRepository.save(conversation);

        log.info("[CONVERSATION] {} turn={}", conversation.getId(), conversation.getTurnCount());

        return new ConversationResponse(
                conversation.getId(),
                assistantMessage,
                conversation.getTurnCount() / 2 // user+assistant = 1 turn
        );
    }

    @Transactional
    public Conversation resolveConversationForStream(ConversationRequest request) {
        Conversation conversation = resolveConversation(request);
        conversation.addTurn("user", request.message());
        return conversationRepository.save(conversation);
    }

    public List<ChatMessage> buildMessages(Conversation conversation) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(
                conversation.getSystemPrompt() != null ? conversation.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT));
        for (ConversationTurn turn : conversation.getTurns()) {
            messages.add(new ChatMessage(turn.getRole(), turn.getContent(), null, null, null));
        }
        return messages;
    }

    @Transactional
    public void saveAssistantTurn(String conversationId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        conversation.addTurn("assistant", content);
        conversationRepository.save(conversation);
    }

    private Conversation resolveConversation(ConversationRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return conversationRepository.findById(request.conversationId())
                    .orElseThrow(() -> new RuntimeException(
                            "Conversation not found: " + request.conversationId()));
        }
        // Create new conversation
        String systemPrompt = request.systemPrompt() != null ? request.systemPrompt() : DEFAULT_SYSTEM_PROMPT;
        return conversationRepository.save(new Conversation(systemPrompt));
    }
}
