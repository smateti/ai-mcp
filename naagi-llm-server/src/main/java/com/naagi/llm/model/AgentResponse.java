package com.naagi.llm.model;

import java.util.List;

public record AgentResponse(
        String answer,
        List<AgentStepResult> steps,
        int totalSteps,
        int totalToolCalls
) {
    public record AgentStepResult(
            String tool,
            String args,
            String result,
            long durationMs
    ) {}
}
