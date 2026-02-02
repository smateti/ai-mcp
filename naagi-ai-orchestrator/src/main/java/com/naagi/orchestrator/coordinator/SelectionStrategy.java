package com.naagi.orchestrator.coordinator;

public enum SelectionStrategy {
    DIRECT,      // 0 or 1 agents — skip selection, no LLM call
    LLM_BASED    // 2+ agents — single LLM classification call to pick the best agent
}
