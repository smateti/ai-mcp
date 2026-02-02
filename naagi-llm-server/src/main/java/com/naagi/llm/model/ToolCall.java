package com.naagi.llm.model;

public record ToolCall(
        String id,
        String type,
        FunctionCall function
) {
    public record FunctionCall(
            String name,
            String arguments
    ) {}

    public static ToolCall function(String id, String name, String arguments) {
        return new ToolCall(id, "function", new FunctionCall(name, arguments));
    }
}
