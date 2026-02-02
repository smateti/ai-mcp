package com.naagi.rag.llm;

import java.util.List;

/**
 * Structured chat message following the OpenAI/LlamaStack message format.
 * Supports system, user, assistant, and tool roles.
 */
public record ChatMessage(
        String role,
        String content,
        String name,
        String toolCallId,
        List<ToolCall> toolCalls
) {
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null, null);
    }

    public static ChatMessage assistantWithToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", null, null, null, toolCalls);
    }

    public static ChatMessage tool(String content, String toolCallId, String name) {
        return new ChatMessage("tool", content, name, toolCallId, null);
    }
}
