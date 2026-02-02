package com.naagi.orchestrator.model;

import java.util.List;

/**
 * Carries agent configuration from tool-registry into the executor.
 * Used to override global defaults when an AgentDefinition is registered for a category.
 */
public class AgentConfig {

    private String agentId;
    private String name;
    private String role;
    private String agentType;
    private int maxSteps;
    private boolean planningEnabled;
    private boolean reflectionEnabled;
    private boolean parallelToolCalls;
    private String systemPromptOverride;
    private String description;
    private String documentAccess;
    private List<AgentToolConfig> tools;
    private List<AgentSkillConfig> skills;

    public AgentConfig() {}

    // --- Getters and Setters ---

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    public boolean isPlanningEnabled() { return planningEnabled; }
    public void setPlanningEnabled(boolean planningEnabled) { this.planningEnabled = planningEnabled; }

    public boolean isReflectionEnabled() { return reflectionEnabled; }
    public void setReflectionEnabled(boolean reflectionEnabled) { this.reflectionEnabled = reflectionEnabled; }

    public boolean isParallelToolCalls() { return parallelToolCalls; }
    public void setParallelToolCalls(boolean parallelToolCalls) { this.parallelToolCalls = parallelToolCalls; }

    public String getSystemPromptOverride() { return systemPromptOverride; }
    public void setSystemPromptOverride(String systemPromptOverride) { this.systemPromptOverride = systemPromptOverride; }

    public String getDocumentAccess() { return documentAccess; }
    public void setDocumentAccess(String documentAccess) { this.documentAccess = documentAccess; }

    public List<AgentToolConfig> getTools() { return tools; }
    public void setTools(List<AgentToolConfig> tools) { this.tools = tools; }

    public List<AgentSkillConfig> getSkills() { return skills; }
    public void setSkills(List<AgentSkillConfig> skills) { this.skills = skills; }

    @Override
    public String toString() {
        return "AgentConfig{agentId='" + agentId + "', name='" + name + "', maxSteps=" + maxSteps +
                ", planning=" + planningEnabled + ", reflection=" + reflectionEnabled + "}";
    }

    /**
     * Tool assignment configuration from an agent definition.
     */
    public static class AgentToolConfig {
        private String toolId;
        private String customDescription;
        private boolean required;

        public AgentToolConfig() {}

        public String getToolId() { return toolId; }
        public void setToolId(String toolId) { this.toolId = toolId; }

        public String getCustomDescription() { return customDescription; }
        public void setCustomDescription(String customDescription) { this.customDescription = customDescription; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    /**
     * Skill metadata from an agent definition, used by the coordinator for agent selection.
     */
    public static class AgentSkillConfig {
        private String name;
        private String description;
        private List<String> tags;

        public AgentSkillConfig() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }
}
