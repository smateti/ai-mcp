package com.naagi.rag.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Tool definition following the OpenAI/LlamaStack function calling format.
 * Passed in the "tools" array of a chat completion request.
 */
public record ToolDefinition(
        String type,
        FunctionDef function
) {
    public record FunctionDef(
            String name,
            String description,
            JsonNode parameters
    ) {}

    /**
     * Create a function-type tool definition.
     *
     * @param name        Tool/function name
     * @param description Human-readable description
     * @param parameters  JSON Schema for the function parameters
     */
    public static ToolDefinition function(String name, String description, JsonNode parameters) {
        return new ToolDefinition("function", new FunctionDef(name, description, parameters));
    }
}
