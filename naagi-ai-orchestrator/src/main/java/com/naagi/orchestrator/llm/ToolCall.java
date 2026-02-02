package com.naagi.orchestrator.llm;

/**
 * Tool call returned by the LLM in a chat completion response.
 * Follows the OpenAI/LlamaStack tool_calls format.
 */
public record ToolCall(
        String id,
        String type,
        FunctionCall function
) {
    public record FunctionCall(
            String name,
            String arguments
    ) {}

    /**
     * Create a function-type tool call.
     */
    public static ToolCall function(String id, String name, String arguments) {
        return new ToolCall(id, "function", new FunctionCall(name, arguments));
    }
}
