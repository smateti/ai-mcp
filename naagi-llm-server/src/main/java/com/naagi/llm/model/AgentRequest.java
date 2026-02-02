package com.naagi.llm.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AgentRequest(
        String message,
        String systemPrompt,
        List<AgentTool> tools,
        Integer maxSteps,
        Double temperature,
        Integer maxTokens,
        boolean stream
) {
    public record AgentTool(
            String name,
            String description,
            JsonNode parameters,
            ToolEndpoint endpoint
    ) {}

    public record ToolEndpoint(
            String url,
            String method
    ) {}
}
