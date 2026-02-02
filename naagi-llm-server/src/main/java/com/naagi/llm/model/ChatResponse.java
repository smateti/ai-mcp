package com.naagi.llm.model;

import java.util.List;

public record ChatResponse(
        String content,
        List<ToolCall> toolCalls,
        String finishReason
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }

    public static ChatResponse text(String content) {
        return new ChatResponse(content, null, "stop");
    }

    public static ChatResponse withToolCalls(List<ToolCall> toolCalls) {
        return new ChatResponse(null, toolCalls, "tool_calls");
    }
}
