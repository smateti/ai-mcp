package com.naagi.llm.model;

public record ConversationResponse(
        String conversationId,
        String message,
        int turnNumber
) {}
