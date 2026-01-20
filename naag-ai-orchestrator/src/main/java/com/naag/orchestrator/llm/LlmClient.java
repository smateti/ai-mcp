package com.naag.orchestrator.llm;

public interface LlmClient {
    String chat(String prompt, double temperature, int maxTokens);
}
