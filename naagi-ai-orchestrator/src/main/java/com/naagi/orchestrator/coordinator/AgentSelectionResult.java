package com.naagi.orchestrator.coordinator;

import com.naagi.orchestrator.model.AgentConfig;

public class AgentSelectionResult {

    private final AgentConfig selectedAgent;
    private final SelectionStrategy strategy;
    private final String reasoning;
    private final long selectionTimeMs;

    public AgentSelectionResult(AgentConfig selectedAgent, SelectionStrategy strategy,
                                 String reasoning, long selectionTimeMs) {
        this.selectedAgent = selectedAgent;
        this.strategy = strategy;
        this.reasoning = reasoning;
        this.selectionTimeMs = selectionTimeMs;
    }

    public AgentConfig getSelectedAgent() { return selectedAgent; }
    public SelectionStrategy getStrategy() { return strategy; }
    public String getReasoning() { return reasoning; }
    public long getSelectionTimeMs() { return selectionTimeMs; }

    public boolean hasAgent() { return selectedAgent != null; }

    @Override
    public String toString() {
        return "AgentSelectionResult{agent=" + (selectedAgent != null ? selectedAgent.getAgentId() : "none") +
                ", strategy=" + strategy + ", timeMs=" + selectionTimeMs + "}";
    }
}
