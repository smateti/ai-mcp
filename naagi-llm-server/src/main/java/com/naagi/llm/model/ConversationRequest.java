package com.naagi.llm.model;

public record ConversationRequest(
        String conversationId,
        String message,
        String systemPrompt,
        Double temperature,
        Integer maxTokens,
        boolean stream
) {}
