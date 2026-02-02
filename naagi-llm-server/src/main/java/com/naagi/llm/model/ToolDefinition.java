package com.naagi.llm.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolDefinition(
        String type,
        FunctionDef function
) {
    public record FunctionDef(
            String name,
            String description,
            JsonNode parameters
    ) {}

    public static ToolDefinition function(String name, String description, JsonNode parameters) {
        return new ToolDefinition("function", new FunctionDef(name, description, parameters));
    }
}
