package com.naagi.rag.llm;

import java.util.List;

/**
 * Structured chat completion response following the OpenAI/LlamaStack format.
 * Contains either text content, tool calls, or both.
 */
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

    /**
     * Create a simple text response.
     */
    public static ChatResponse text(String content) {
        return new ChatResponse(content, null, "stop");
    }

    /**
     * Create a tool call response.
     */
    public static ChatResponse withToolCalls(List<ToolCall> toolCalls) {
        return new ChatResponse(null, toolCalls, "tool_calls");
    }
}
