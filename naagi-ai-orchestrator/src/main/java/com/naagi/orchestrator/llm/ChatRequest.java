package com.naagi.orchestrator.llm;

import java.util.List;

/**
 * Structured chat completion request following the OpenAI/LlamaStack format.
 * Supports messages array, optional tool definitions, and tool_choice.
 */
public record ChatRequest(
        List<ChatMessage> messages,
        double temperature,
        int maxTokens,
        boolean stream,
        List<ToolDefinition> tools,
        String toolChoice
) {
    /**
     * Create a simple chat request without tools.
     */
    public static ChatRequest of(List<ChatMessage> messages, double temperature, int maxTokens) {
        return new ChatRequest(messages, temperature, maxTokens, false, null, null);
    }

    /**
     * Create a chat request with tool definitions.
     *
     * @param toolChoice "auto" (model decides), "none" (no tools), "required" (must call a tool)
     */
    public static ChatRequest withTools(List<ChatMessage> messages, List<ToolDefinition> tools,
                                        String toolChoice, double temperature, int maxTokens) {
        return new ChatRequest(messages, temperature, maxTokens, false, tools, toolChoice);
    }

    public boolean hasTools() {
        return tools != null && !tools.isEmpty();
    }
}
