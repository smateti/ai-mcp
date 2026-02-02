package com.naagi.llm.model;

import java.util.List;

public record ChatRequest(
        List<ChatMessage> messages,
        double temperature,
        int maxTokens,
        boolean stream,
        List<ToolDefinition> tools,
        String toolChoice
) {
    public static ChatRequest of(List<ChatMessage> messages, double temperature, int maxTokens) {
        return new ChatRequest(messages, temperature, maxTokens, false, null, null);
    }

    public static ChatRequest withTools(List<ChatMessage> messages, List<ToolDefinition> tools,
                                        String toolChoice, double temperature, int maxTokens) {
        return new ChatRequest(messages, temperature, maxTokens, false, tools, toolChoice);
    }

    public boolean hasTools() {
        return tools != null && !tools.isEmpty();
    }
}
