package com.naagi.orchestrator.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naagi.orchestrator.llm.*;
import com.naagi.orchestrator.model.AgentConfig;
import com.naagi.orchestrator.service.ToolRegistryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Coordinator selects the right agent for a user message within a category.
 * This is a function, not an agent — it makes one selection decision then gets out of the way.
 *
 * Strategies:
 *  - DIRECT: 0 or 1 agents → no LLM call needed
 *  - LLM_BASED: 2+ agents → single classification call using native tool calling
 */
@Service
@Slf4j
public class CoordinatorService {

    private final ToolRegistryClient toolRegistryClient;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CoordinatorService(ToolRegistryClient toolRegistryClient, LlmClient llmClient) {
        this.toolRegistryClient = toolRegistryClient;
        this.llmClient = llmClient;
    }

    /**
     * Select the best agent for a user message within the given category.
     * Returns null selectedAgent if no agents are available.
     */
    public AgentSelectionResult selectAgent(String userMessage, String categoryId) {
        long start = System.currentTimeMillis();

        if (categoryId == null || categoryId.isBlank()) {
            log.debug("[COORDINATOR] No categoryId provided, returning no agent");
            return new AgentSelectionResult(null, SelectionStrategy.DIRECT, "No category specified", elapsed(start));
        }

        List<AgentConfig> agents = toolRegistryClient.getAgentsForCategory(categoryId);

        if (agents.isEmpty()) {
            log.info("[COORDINATOR] No active agents for category {}", categoryId);
            return new AgentSelectionResult(null, SelectionStrategy.DIRECT, "No agents registered", elapsed(start));
        }

        if (agents.size() == 1) {
            AgentConfig agent = agents.get(0);
            log.info("[COORDINATOR] DIRECT selection: {} ({}) for category {}", agent.getAgentId(), agent.getName(), categoryId);
            return new AgentSelectionResult(agent, SelectionStrategy.DIRECT, "Only agent in category", elapsed(start));
        }

        // 2+ agents — use LLM with native tool calling to classify
        log.info("[COORDINATOR] LLM_BASED selection among {} agents for category {}", agents.size(), categoryId);
        return selectWithLlm(userMessage, agents, start);
    }

    private AgentSelectionResult selectWithLlm(String userMessage, List<AgentConfig> agents, long start) {
        try {
            String systemPrompt = buildSystemPrompt(agents);
            ToolDefinition selectTool = buildSelectAgentTool(agents.size());

            log.debug("[COORDINATOR] System prompt:\n{}", systemPrompt);

            ChatRequest request = ChatRequest.withTools(
                    List.of(
                            ChatMessage.system(systemPrompt),
                            ChatMessage.user(userMessage)
                    ),
                    List.of(selectTool),
                    "required",  // force the model to call the tool
                    0.0, 50      // low temperature, enough tokens for tool call JSON
            );

            ChatResponse response = llmClient.chat(request);

            // Parse agent number from tool call response
            int selectedIndex = parseToolCallResponse(response, agents.size());
            AgentConfig selected = agents.get(selectedIndex);

            long timeMs = elapsed(start);
            log.info("[COORDINATOR] LLM selected agent #{}: {} ({}) in {}ms",
                    selectedIndex + 1, selected.getAgentId(), selected.getName(), timeMs);

            return new AgentSelectionResult(selected, SelectionStrategy.LLM_BASED,
                    "LLM selected agent #" + (selectedIndex + 1), timeMs);

        } catch (Exception e) {
            log.warn("[COORDINATOR] LLM selection failed, falling back to first agent: {}", e.getMessage());
            AgentConfig fallback = agents.get(0);
            return new AgentSelectionResult(fallback, SelectionStrategy.LLM_BASED,
                    "Fallback to first agent (LLM error)", elapsed(start));
        }
    }

    /**
     * Build a system prompt that describes the available agents.
     */
    private String buildSystemPrompt(List<AgentConfig> agents) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an agent router. Based on the user's question, select the best agent by calling the select_agent function.\n\n");
        sb.append("Available agents:\n\n");

        for (int i = 0; i < agents.size(); i++) {
            AgentConfig agent = agents.get(i);
            sb.append("Agent ").append(i + 1).append(": ").append(agent.getName()).append("\n");
            if (agent.getDescription() != null && !agent.getDescription().isBlank()) {
                sb.append("  Description: ").append(agent.getDescription()).append("\n");
            }
            if (agent.getTools() != null && !agent.getTools().isEmpty()) {
                sb.append("  Tools: ");
                for (int j = 0; j < agent.getTools().size(); j++) {
                    AgentConfig.AgentToolConfig tool = agent.getTools().get(j);
                    if (j > 0) sb.append(", ");
                    if (tool.getCustomDescription() != null && !tool.getCustomDescription().isBlank()) {
                        sb.append(tool.getCustomDescription());
                    } else {
                        sb.append(tool.getToolId());
                    }
                }
                sb.append("\n");
            }
            if (agent.getSkills() != null && !agent.getSkills().isEmpty()) {
                sb.append("  Skills: ");
                for (int j = 0; j < agent.getSkills().size(); j++) {
                    AgentConfig.AgentSkillConfig skill = agent.getSkills().get(j);
                    if (j > 0) sb.append(", ");
                    sb.append(skill.getName());
                    if (skill.getDescription() != null) {
                        sb.append(" (").append(skill.getDescription()).append(")");
                    }
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Build a tool definition for agent selection using native function calling.
     * The model calls select_agent(agent_number=N) to pick an agent.
     */
    private ToolDefinition buildSelectAgentTool(int agentCount) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");

        ObjectNode properties = params.putObject("properties");
        ObjectNode agentNumberProp = properties.putObject("agent_number");
        agentNumberProp.put("type", "integer");
        agentNumberProp.put("description", "The number of the best matching agent (1-" + agentCount + ")");
        agentNumberProp.put("minimum", 1);
        agentNumberProp.put("maximum", agentCount);

        params.putArray("required").add("agent_number");

        return ToolDefinition.function(
                "select_agent",
                "Select the best agent to handle the user's question. Call this with the agent number.",
                params
        );
    }

    /**
     * Parse the agent number from the LLM's tool call response.
     * Falls back to parsing content text if no tool call is present.
     */
    private int parseToolCallResponse(ChatResponse response, int agentCount) {
        // First try: parse from tool call (native function calling)
        if (response.hasToolCalls()) {
            ToolCall toolCall = response.toolCalls().get(0);
            String args = toolCall.function().arguments();
            log.debug("[COORDINATOR] Tool call: {}({})", toolCall.function().name(), args);
            try {
                JsonNode argsNode = objectMapper.readTree(args);
                int agentNumber = argsNode.has("agent_number") ? argsNode.get("agent_number").asInt() : 0;
                if (agentNumber >= 1 && agentNumber <= agentCount) {
                    return agentNumber - 1;
                }
                log.warn("[COORDINATOR] agent_number {} out of range (1-{})", agentNumber, agentCount);
            } catch (Exception e) {
                log.warn("[COORDINATOR] Failed to parse tool call args '{}': {}", args, e.getMessage());
            }
        }

        // Second try: parse from content text (fallback for models that don't use tool calling)
        String content = response.content() != null ? response.content().trim() : "";
        if (!content.isEmpty()) {
            log.debug("[COORDINATOR] No tool call, parsing content: '{}'", content);
            return parseAgentNumberFromText(content, agentCount);
        }

        log.warn("[COORDINATOR] No tool call and no content in response, defaulting to agent 1");
        return 0;
    }

    /**
     * Parse agent number from free-text LLM output. Expects a number 1-N.
     * Falls back to 0 (first agent) if parsing fails.
     */
    private int parseAgentNumberFromText(String content, int agentCount) {
        String digits = content.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            log.warn("[COORDINATOR] Could not parse agent number from text: '{}'", content);
            return 0;
        }

        int number = Integer.parseInt(digits);
        if (number < 1 || number > agentCount) {
            log.warn("[COORDINATOR] Agent number {} out of range (1-{}), defaulting to 1", number, agentCount);
            return 0;
        }

        return number - 1;
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
