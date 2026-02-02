package com.naagi.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naagi.orchestrator.entity.AgentSession;
import com.naagi.orchestrator.entity.AgentStep;
import com.naagi.orchestrator.llm.*;
import com.naagi.orchestrator.model.AgentConfig;
import com.naagi.orchestrator.repository.AgentSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@Service
@Slf4j
public class AgentStreamExecutor {

    private final LlmClient llmClient;
    private final McpGatewayClient mcpGatewayClient;
    private final ToolRegistryClient toolRegistryClient;
    private final AgentSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Value("${naagi.agent.max-steps:10}")
    private int maxSteps;

    @Value("${naagi.agent.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${naagi.agent.planning.enabled:false}")
    private boolean planningEnabled;

    @Value("${naagi.agent.reflection.enabled:false}")
    private boolean reflectionEnabled;

    public AgentStreamExecutor(LlmClient llmClient,
                               McpGatewayClient mcpGatewayClient,
                               ToolRegistryClient toolRegistryClient,
                               AgentSessionRepository sessionRepository,
                               ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.mcpGatewayClient = mcpGatewayClient;
        this.toolRegistryClient = toolRegistryClient;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Overloaded: execute with agent-specific configuration from the registry.
     * Uses the agent's role, maxSteps, planning/reflection settings, and tool list
     * instead of global defaults.
     */
    public void executeStream(String userMessage, String categoryId, String previousSessionId, SseEmitter emitter, AgentConfig agentConfig) {
        executeStream(userMessage, categoryId, previousSessionId, null, emitter, agentConfig);
    }

    public void executeStream(String userMessage, String categoryId, String previousSessionId,
                               List<com.naagi.orchestrator.model.OrchestrationRequest.ConversationContext> conversationContext,
                               SseEmitter emitter, AgentConfig agentConfig) {
        log.info("[AGENT-STREAM] Using agent config: {} ({})", agentConfig.getAgentId(), agentConfig.getName());
        AgentSession session = new AgentSession(userMessage, categoryId);
        long startTime = System.currentTimeMillis();

        try {
            // Load tools — if agent has specific tool assignments, filter to those only
            List<JsonNode> availableToolsJson = loadAvailableTools(categoryId);
            if (agentConfig.getTools() != null && !agentConfig.getTools().isEmpty()) {
                availableToolsJson = filterToolsByAgent(availableToolsJson, agentConfig);
            }
            Map<String, String> nameToToolId = buildNameToToolIdMap(availableToolsJson);
            List<ToolDefinition> tools = buildToolDefinitions(availableToolsJson);

            // Apply custom descriptions from agent config
            if (agentConfig.getTools() != null) {
                tools = applyCustomDescriptions(tools, agentConfig);
            }

            sendEvent(emitter, "session_start", Map.of(
                    "sessionId", session.getSessionId(),
                    "toolCount", tools.size(),
                    "agentId", agentConfig.getAgentId(),
                    "agentName", agentConfig.getName()));

            // Build system prompt: use override if set, otherwise base + role
            String systemPrompt = buildSystemPrompt(agentConfig);

            // Build conversation context: prefer explicit context from chat-app (reply-to),
            // fall back to session-based lookup if available
            String effectiveMessage = userMessage;
            if (conversationContext != null && !conversationContext.isEmpty()) {
                log.info("[AGENT-STREAM] Using reply-to context ({} entries) for message: {}",
                        conversationContext.size(), userMessage.substring(0, Math.min(100, userMessage.length())));
                effectiveMessage = buildContextFromReplyTo(conversationContext) + userMessage;
                log.info("[AGENT-STREAM] Effective message with context (first 500 chars): {}",
                        effectiveMessage.substring(0, Math.min(500, effectiveMessage.length())));
            } else {
                log.info("[AGENT-STREAM] No reply-to context. conversationContext={}, previousSessionId={}",
                        conversationContext, previousSessionId);
                String contextPrefix = extractConversationContext(previousSessionId);
                if (contextPrefix != null) {
                    effectiveMessage = contextPrefix + userMessage;
                }
            }

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.user(effectiveMessage));

            // Use agent-level settings
            int effectiveMaxSteps = agentConfig.getMaxSteps() > 0 ? agentConfig.getMaxSteps() : this.maxSteps;
            boolean effectivePlanning = agentConfig.isPlanningEnabled();
            boolean effectiveReflection = agentConfig.isReflectionEnabled();

            // Planning phase
            if (effectivePlanning && tools.size() > 1) {
                String plan = generatePlan(messages, tools);
                if (plan != null) {
                    if (plan.contains("NO_TOOL_MATCH")) {
                        String toolSummary = buildToolSummary(tools);
                        String noMatchMsg = "Sorry, this capability is not available in the current category. "
                                + "The tools available here are: " + toolSummary + ". "
                                + "Please try a different category or rephrase your question.";
                        log.info("[AGENT-STREAM] No tool match for query in session {}", session.getSessionId());
                        session.complete(noMatchMsg);
                        streamTokens(emitter, noMatchMsg);
                        sendEvent(emitter, "done", Map.of(
                                "sessionId", session.getSessionId(),
                                "totalSteps", 0,
                                "totalToolCalls", 0,
                                "status", "NO_TOOL_MATCH"));
                        sessionRepository.save(session);
                        emitter.complete();
                        return;
                    }
                    session.addStep(AgentStep.plan(0, plan));
                    sendEvent(emitter, "plan_created", Map.of("plan", plan));
                    log.info("[AGENT-STREAM] Plan generated for session {}", session.getSessionId());
                    messages.add(ChatMessage.assistant(plan));
                    messages.add(ChatMessage.user(
                            "Good plan. Now execute it step by step using the available tools. "
                            + "Start with the first step."));
                }
            }

            Set<String> seenToolCalls = new HashSet<>();
            int toolCallCount = 0;
            int consecutiveLoops = 0;

            for (int step = 0; step < effectiveMaxSteps; step++) {
                if (System.currentTimeMillis() - startTime > timeoutSeconds * 1000L) {
                    sendEvent(emitter, "error", Map.of("message", "Agent timed out"));
                    session.fail("Agent timed out after " + timeoutSeconds + " seconds");
                    break;
                }

                // Force final answer if agent is stuck in a loop
                if (consecutiveLoops >= 1) {
                    log.warn("[AGENT-STREAM] Breaking out of loop after {} consecutive duplicate calls", consecutiveLoops);
                    messages.add(ChatMessage.user(
                            "STOP calling tools. You have been repeating the same call. "
                            + "Based on the data you already received, provide your final answer NOW. "
                            + "If the first tool call returned data, summarize it. "
                            + "If it returned empty results, tell the user no results were found."));
                    ChatRequest forceRequest = ChatRequest.of(messages, 0.2, 1024);
                    ChatResponse forceResponse = llmClient.chat(forceRequest);
                    String forcedAnswer = forceResponse.content();
                    session.addStep(AgentStep.finalAnswer(step + 1, forcedAnswer));
                    session.setTotalToolCalls(toolCallCount);
                    session.complete(forcedAnswer);
                    streamTokens(emitter, forcedAnswer);
                    break;
                }

                ChatRequest request = ChatRequest.withTools(messages, tools, "auto", 0.2, 1024);
                ChatResponse response = llmClient.chat(request);

                if (response.hasToolCalls()) {
                    messages.add(ChatMessage.assistantWithToolCalls(response.toolCalls()));
                    boolean allLooped = true;
                    for (ToolCall toolCall : response.toolCalls()) {
                        String toolName = toolCall.function().name();
                        String argsJson = toolCall.function().arguments();
                        sendEvent(emitter, "agent_step", Map.of(
                                "step", step + 1, "type", "tool_call",
                                "tool", toolName, "args", argsJson != null ? argsJson : "{}"));
                        String callSignature = toolName + ":" + argsJson;
                        if (seenToolCalls.contains(callSignature)) {
                            messages.add(ChatMessage.tool(
                                    "ERROR: You already called this exact tool with these exact arguments. "
                                    + "Do NOT call it again. Either try a DIFFERENT tool with DIFFERENT parameters, "
                                    + "or provide your final answer based on the data you already have.",
                                    toolCall.id(), toolName));
                            sendEvent(emitter, "tool_result", Map.of(
                                    "step", step + 1, "tool", toolName, "error", "Loop detected"));
                            continue;
                        }
                        allLooped = false;
                        seenToolCalls.add(callSignature);
                        long toolStart = System.currentTimeMillis();
                        String registryToolId = nameToToolId.getOrDefault(toolName, toolName);
                        String toolResult = executeTool(registryToolId, argsJson, categoryId);
                        long toolDuration = System.currentTimeMillis() - toolStart;
                        messages.add(ChatMessage.tool(toolResult, toolCall.id(), toolName));
                        session.addStep(AgentStep.toolCall(step + 1, toolName, argsJson, toolResult, toolDuration));
                        toolCallCount++;
                        sendEvent(emitter, "tool_result", Map.of(
                                "step", step + 1, "tool", toolName,
                                "durationMs", toolDuration, "result", truncateForEvent(toolResult, 500)));
                    }
                    consecutiveLoops = allLooped ? consecutiveLoops + 1 : 0;
                    if (effectiveReflection && toolCallCount > 0 && toolCallCount % 2 == 0) {
                        String reflection = reflect(messages, userMessage);
                        if (reflection != null) {
                            session.addStep(AgentStep.reflection(step + 1, reflection));
                            sendEvent(emitter, "reflection", Map.of("step", step + 1, "reflection", reflection));
                        }
                    }
                } else {
                    String answer = response.content();
                    ToolCall embeddedCall = extractEmbeddedToolCall(answer, tools);
                    if (embeddedCall != null) {
                        log.info("[AGENT-STREAM] Detected embedded tool call: {}", embeddedCall.function().name());
                        messages.add(ChatMessage.assistantWithToolCalls(List.of(embeddedCall)));
                        String toolName = embeddedCall.function().name();
                        String argsJson = embeddedCall.function().arguments();
                        sendEvent(emitter, "agent_step", Map.of(
                                "step", step + 1, "type", "tool_call",
                                "tool", toolName, "args", argsJson != null ? argsJson : "{}"));
                        String callSignature = toolName + ":" + argsJson;
                        if (seenToolCalls.contains(callSignature)) {
                            messages.add(ChatMessage.tool(
                                    "ERROR: Loop detected", embeddedCall.id(), toolName));
                            continue;
                        }
                        seenToolCalls.add(callSignature);
                        long toolStart = System.currentTimeMillis();
                        String registryToolId = nameToToolId.getOrDefault(toolName, toolName);
                        String toolResult = executeTool(registryToolId, argsJson, categoryId);
                        long toolDuration = System.currentTimeMillis() - toolStart;
                        messages.add(ChatMessage.tool(toolResult, embeddedCall.id(), toolName));
                        session.addStep(AgentStep.toolCall(step + 1, toolName, argsJson, toolResult, toolDuration));
                        toolCallCount++;
                        sendEvent(emitter, "tool_result", Map.of(
                                "step", step + 1, "tool", toolName,
                                "durationMs", toolDuration, "result", truncateForEvent(toolResult, 500)));
                        continue;
                    }

                    // Guard: if tools were called but LLM still says "not available",
                    // re-prompt to get a proper summary of what was found
                    if (toolCallCount > 0 && answer != null
                            && answer.toLowerCase().contains("not available in the current category")) {
                        log.warn("[AGENT-STREAM] LLM said 'not available' after {} tool calls — re-prompting", toolCallCount);
                        messages.add(ChatMessage.assistant(answer));
                        messages.add(ChatMessage.user(
                                "WRONG. You already called tools and received results. "
                                + "Do NOT say 'not available'. Summarize the actual tool results you received. "
                                + "If the results were empty, say 'No results were found' with specifics."));
                        ChatRequest correctionReq = ChatRequest.of(messages, 0.2, 1024);
                        ChatResponse correctionResp = llmClient.chat(correctionReq);
                        answer = correctionResp.content();
                    }

                    session.addStep(AgentStep.finalAnswer(step + 1, answer));
                    session.setTotalToolCalls(toolCallCount);
                    session.complete(answer);
                    streamTokens(emitter, answer);
                    break;
                }
            }

            if (!session.isCompleted() && session.getFinalAnswer() == null) {
                String fallback = "I reached the maximum number of steps. Here is what I found so far based on the tool results above.";
                session.setTotalToolCalls(toolCallCount);
                session.maxStepsReached(fallback);
                streamTokens(emitter, fallback);
            }

            sendEvent(emitter, "done", Map.of(
                    "sessionId", session.getSessionId(),
                    "totalSteps", session.getTotalSteps(),
                    "totalToolCalls", toolCallCount,
                    "status", session.getStatus().name()));
            sessionRepository.save(session);
            emitter.complete();

        } catch (Exception e) {
            log.error("[AGENT-STREAM] Session {} failed", session.getSessionId(), e);
            session.fail("Agent execution failed: " + e.getMessage());
            sessionRepository.save(session);
            try {
                sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    /**
     * Original method: execute with global defaults (no agent config).
     */
    public void executeStream(String userMessage, String categoryId, String previousSessionId, SseEmitter emitter) {
        executeStream(userMessage, categoryId, previousSessionId, null, emitter);
    }

    public void executeStream(String userMessage, String categoryId, String previousSessionId,
                               List<com.naagi.orchestrator.model.OrchestrationRequest.ConversationContext> conversationContext,
                               SseEmitter emitter) {
        AgentSession session = new AgentSession(userMessage, categoryId);
        long startTime = System.currentTimeMillis();

        try {
            // Load available tools and build name→toolId mapping
            List<JsonNode> availableToolsJson = loadAvailableTools(categoryId);
            Map<String, String> nameToToolId = buildNameToToolIdMap(availableToolsJson);
            List<ToolDefinition> tools = buildToolDefinitions(availableToolsJson);

            sendEvent(emitter, "session_start", Map.of(
                    "sessionId", session.getSessionId(),
                    "toolCount", tools.size()));

            // Build conversation context: prefer explicit context from chat-app (reply-to),
            // fall back to session-based lookup if available
            String effectiveMessage = userMessage;
            if (conversationContext != null && !conversationContext.isEmpty()) {
                log.info("[AGENT-STREAM-DEFAULT] Using reply-to context ({} entries) for message: {}",
                        conversationContext.size(), userMessage.substring(0, Math.min(100, userMessage.length())));
                effectiveMessage = buildContextFromReplyTo(conversationContext) + userMessage;
                log.info("[AGENT-STREAM-DEFAULT] Effective message with context (first 500 chars): {}",
                        effectiveMessage.substring(0, Math.min(500, effectiveMessage.length())));
            } else {
                log.info("[AGENT-STREAM-DEFAULT] No reply-to context. conversationContext={}, previousSessionId={}",
                        conversationContext, previousSessionId);
                String contextPrefix = extractConversationContext(previousSessionId);
                if (contextPrefix != null) {
                    effectiveMessage = contextPrefix + userMessage;
                }
            }

            // Initialize conversation
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(AgentExecutor.DEFAULT_SYSTEM_PROMPT + "\n\n" + currentDateContext()));
            messages.add(ChatMessage.user(effectiveMessage));

            // Planning phase (also serves as relevance check)
            if (planningEnabled && tools.size() > 1) {
                String plan = generatePlan(messages, tools);
                if (plan != null) {
                    // Check if the plan indicates no tool matches the query
                    if (plan.contains("NO_TOOL_MATCH")) {
                        String toolSummary = buildToolSummary(tools);
                        String noMatchMsg = "Sorry, this capability is not available in the current category. "
                                + "The tools available here are: " + toolSummary + ". "
                                + "Please try a different category or rephrase your question.";
                        log.info("[AGENT-STREAM] No tool match for query in session {}", session.getSessionId());
                        session.complete(noMatchMsg);
                        streamTokens(emitter, noMatchMsg);

                        sendEvent(emitter, "done", Map.of(
                                "sessionId", session.getSessionId(),
                                "totalSteps", 0,
                                "totalToolCalls", 0,
                                "status", "NO_TOOL_MATCH"));
                        sessionRepository.save(session);
                        emitter.complete();
                        return;
                    }

                    session.addStep(AgentStep.plan(0, plan));
                    sendEvent(emitter, "plan_created", Map.of(
                            "plan", plan));
                    log.info("[AGENT-STREAM] Plan generated for session {}", session.getSessionId());
                    messages.add(ChatMessage.assistant(plan));
                    messages.add(ChatMessage.user(
                            "Good plan. Now execute it step by step using the available tools. "
                            + "Start with the first step."));
                }
            }

            Set<String> seenToolCalls = new HashSet<>();
            int toolCallCount = 0;
            int consecutiveLoops = 0;

            for (int step = 0; step < maxSteps; step++) {
                // Check timeout
                if (System.currentTimeMillis() - startTime > timeoutSeconds * 1000L) {
                    sendEvent(emitter, "error", Map.of("message", "Agent timed out"));
                    session.fail("Agent timed out after " + timeoutSeconds + " seconds");
                    break;
                }

                // Force final answer if agent is stuck in a loop
                if (consecutiveLoops >= 1) {
                    log.warn("[AGENT-STREAM] Breaking out of loop after {} consecutive duplicate calls", consecutiveLoops);
                    messages.add(ChatMessage.user(
                            "STOP calling tools. You have been repeating the same call. "
                            + "Based on the data you already received, provide your final answer NOW. "
                            + "If the first tool call returned data, summarize it. "
                            + "If it returned empty results, tell the user no results were found."));
                    ChatRequest forceRequest = ChatRequest.of(messages, 0.2, 1024);
                    ChatResponse forceResponse = llmClient.chat(forceRequest);
                    String forcedAnswer = forceResponse.content();
                    session.addStep(AgentStep.finalAnswer(step + 1, forcedAnswer));
                    session.setTotalToolCalls(toolCallCount);
                    session.complete(forcedAnswer);
                    streamTokens(emitter, forcedAnswer);
                    break;
                }

                // Call LLM with tools
                ChatRequest request = ChatRequest.withTools(messages, tools, "auto", 0.2, 1024);
                ChatResponse response = llmClient.chat(request);

                if (response.hasToolCalls()) {
                    messages.add(ChatMessage.assistantWithToolCalls(response.toolCalls()));
                    boolean allLooped = true;

                    for (ToolCall toolCall : response.toolCalls()) {
                        String toolName = toolCall.function().name();
                        String argsJson = toolCall.function().arguments();

                        // Emit tool call event
                        sendEvent(emitter, "agent_step", Map.of(
                                "step", step + 1,
                                "type", "tool_call",
                                "tool", toolName,
                                "args", argsJson != null ? argsJson : "{}"));

                        // Loop detection
                        String callSignature = toolName + ":" + argsJson;
                        if (seenToolCalls.contains(callSignature)) {
                            messages.add(ChatMessage.tool(
                                    "ERROR: You already called this exact tool with these exact arguments. "
                                    + "Do NOT call it again. Either try a DIFFERENT tool with DIFFERENT parameters, "
                                    + "or provide your final answer based on the data you already have.",
                                    toolCall.id(), toolName));
                            sendEvent(emitter, "tool_result", Map.of(
                                    "step", step + 1,
                                    "tool", toolName,
                                    "error", "Loop detected"));
                            continue;
                        }
                        allLooped = false;
                        seenToolCalls.add(callSignature);

                        // Execute tool (resolve name→toolId for registry lookup)
                        long toolStart = System.currentTimeMillis();
                        String registryToolId = nameToToolId.getOrDefault(toolName, toolName);
                        String toolResult = executeTool(registryToolId, argsJson, categoryId);
                        long toolDuration = System.currentTimeMillis() - toolStart;

                        messages.add(ChatMessage.tool(toolResult, toolCall.id(), toolName));
                        session.addStep(AgentStep.toolCall(step + 1, toolName, argsJson, toolResult, toolDuration));
                        toolCallCount++;

                        // Emit tool result event
                        sendEvent(emitter, "tool_result", Map.of(
                                "step", step + 1,
                                "tool", toolName,
                                "durationMs", toolDuration,
                                "result", truncateForEvent(toolResult, 500)));
                    }
                    consecutiveLoops = allLooped ? consecutiveLoops + 1 : 0;

                    // Self-reflection after every 2 tool calls
                    if (reflectionEnabled && toolCallCount > 0 && toolCallCount % 2 == 0) {
                        String reflection = reflect(messages, userMessage);
                        if (reflection != null) {
                            session.addStep(AgentStep.reflection(step + 1, reflection));
                            sendEvent(emitter, "reflection", Map.of(
                                    "step", step + 1,
                                    "reflection", reflection));
                        }
                    }
                } else {
                    // Check if the LLM embedded a tool call in its text response
                    // (common with smaller models that don't always use native tool calling)
                    String answer = response.content();
                    ToolCall embeddedCall = extractEmbeddedToolCall(answer, tools);
                    if (embeddedCall != null) {
                        log.info("[AGENT-STREAM] Detected embedded tool call in text: {}", embeddedCall.function().name());
                        // Treat as a tool call — add assistant message with the text, then process tool call
                        messages.add(ChatMessage.assistantWithToolCalls(List.of(embeddedCall)));

                        String toolName = embeddedCall.function().name();
                        String argsJson = embeddedCall.function().arguments();

                        sendEvent(emitter, "agent_step", Map.of(
                                "step", step + 1,
                                "type", "tool_call",
                                "tool", toolName,
                                "args", argsJson != null ? argsJson : "{}"));

                        String callSignature = toolName + ":" + argsJson;
                        if (seenToolCalls.contains(callSignature)) {
                            messages.add(ChatMessage.tool(
                                    "ERROR: Loop detected: same tool called with same arguments", embeddedCall.id(), toolName));
                            continue;
                        }
                        seenToolCalls.add(callSignature);

                        long toolStart = System.currentTimeMillis();
                        String registryToolId = nameToToolId.getOrDefault(toolName, toolName);
                        String toolResult = executeTool(registryToolId, argsJson, categoryId);
                        long toolDuration = System.currentTimeMillis() - toolStart;

                        messages.add(ChatMessage.tool(toolResult, embeddedCall.id(), toolName));
                        session.addStep(AgentStep.toolCall(step + 1, toolName, argsJson, toolResult, toolDuration));
                        toolCallCount++;

                        sendEvent(emitter, "tool_result", Map.of(
                                "step", step + 1,
                                "tool", toolName,
                                "durationMs", toolDuration,
                                "result", truncateForEvent(toolResult, 500)));
                        continue;
                    }

                    // Guard: if tools were called but LLM still says "not available",
                    // re-prompt to get a proper summary of what was found
                    if (toolCallCount > 0 && answer != null
                            && answer.toLowerCase().contains("not available in the current category")) {
                        log.warn("[AGENT-STREAM] LLM said 'not available' after {} tool calls — re-prompting", toolCallCount);
                        messages.add(ChatMessage.assistant(answer));
                        messages.add(ChatMessage.user(
                                "WRONG. You already called tools and received results. "
                                + "Do NOT say 'not available'. Summarize the actual tool results you received. "
                                + "If the results were empty, say 'No results were found' with specifics."));
                        ChatRequest correctionReq = ChatRequest.of(messages, 0.2, 1024);
                        ChatResponse correctionResp = llmClient.chat(correctionReq);
                        answer = correctionResp.content();
                    }

                    // Final answer — stream it token by token
                    session.addStep(AgentStep.finalAnswer(step + 1, answer));
                    session.setTotalToolCalls(toolCallCount);
                    session.complete(answer);

                    streamTokens(emitter, answer);
                    break;
                }
            }

            // Max steps reached
            if (!session.isCompleted() && session.getFinalAnswer() == null) {
                String fallback = "I reached the maximum number of steps. Here is what I found so far based on the tool results above.";
                session.setTotalToolCalls(toolCallCount);
                session.maxStepsReached(fallback);
                streamTokens(emitter, fallback);
            }

            sendEvent(emitter, "done", Map.of(
                    "sessionId", session.getSessionId(),
                    "totalSteps", session.getTotalSteps(),
                    "totalToolCalls", toolCallCount,
                    "status", session.getStatus().name()));

            sessionRepository.save(session);
            emitter.complete();

        } catch (Exception e) {
            log.error("[AGENT-STREAM] Session {} failed", session.getSessionId(), e);
            session.fail("Agent execution failed: " + e.getMessage());
            sessionRepository.save(session);
            try {
                sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    private String generatePlan(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            StringBuilder toolList = new StringBuilder();
            for (ToolDefinition tool : tools) {
                toolList.append("- ").append(tool.function().name())
                        .append(": ").append(tool.function().description()).append("\n");
            }

            String planPrompt = """
                    Before executing, create a brief step-by-step plan to answer the user's question.

                    Available tools:
                    %s

                    RULES:
                    - Output 2-5 numbered steps
                    - Each step should specify which tool to use and why
                    - Keep it concise — one line per step
                    - If the question is simple and needs only one tool call, output just one step
                    - ALWAYS try to use the available tools even if the match is indirect. \
                    For example, if the user asks about "failed jobs" and you have a tool to list job runs, USE IT — \
                    you can filter or inspect results to find failures.
                    - Only respond with NO_TOOL_MATCH if the user's question is COMPLETELY unrelated \
                    to ALL available tools (e.g., asking about weather when you only have database tools). \
                    When in doubt, make a plan and try the tools.
                    - CONVERSATION CONTEXT: If there are earlier messages in the conversation about a specific \
                    application or entity, and the current question does not mention a different one, your plan \
                    MUST scope tool calls to that same application/entity. Do NOT broaden the search to other apps.

                    Plan:""".formatted(toolList.toString());

            List<ChatMessage> planMessages = new ArrayList<>(messages);
            planMessages.add(ChatMessage.user(planPrompt));

            ChatRequest request = ChatRequest.of(planMessages, 0.2, 512);
            ChatResponse response = llmClient.chat(request);

            String plan = response.content();
            if (plan != null && !plan.isBlank()) {
                return plan.trim();
            }
        } catch (Exception e) {
            log.warn("[AGENT-STREAM] Planning phase failed: {}", e.getMessage());
        }
        return null;
    }

    private String reflect(List<ChatMessage> messages, String originalQuestion) {
        try {
            String reflectPrompt = """
                    Review the tool results gathered so far for the question: "%s"

                    Briefly assess:
                    1. Have we gathered enough information to answer the question?
                    2. Are any tool results missing, incomplete, or erroneous?
                    3. Should we adjust our approach?

                    Reply in 1-2 sentences. If everything looks good, say "On track." \
                    If adjustment is needed, describe what to do differently."""
                    .formatted(originalQuestion);

            List<ChatMessage> reflectMessages = new ArrayList<>(messages);
            reflectMessages.add(ChatMessage.user(reflectPrompt));

            ChatRequest request = ChatRequest.of(reflectMessages, 0.1, 256);
            ChatResponse response = llmClient.chat(request);

            String reflection = response.content();
            if (reflection != null && !reflection.isBlank()) {
                String lower = reflection.toLowerCase();
                if (!lower.contains("on track") && !lower.contains("looks good")
                        && !lower.contains("sufficient")) {
                    messages.add(ChatMessage.user(
                            "Reflection: " + reflection + " — Adjust your next action accordingly."));
                }
                return reflection.trim();
            }
        } catch (Exception e) {
            log.warn("[AGENT-STREAM] Reflection failed: {}", e.getMessage());
        }
        return null;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.warn("[AGENT-STREAM] Failed to send SSE event {}: {}", eventName, e.getMessage());
        }
    }

    private void streamTokens(SseEmitter emitter, String text) {
        if (text == null || text.isBlank()) return;
        String[] words = text.split("(?<=\\s)");
        for (String word : words) {
            try {
                emitter.send(SseEmitter.event()
                        .name("token")
                        .data(objectMapper.writeValueAsString(Map.of("t", word))));
                Thread.sleep(10);
            } catch (Exception e) {
                log.warn("[AGENT-STREAM] Failed to stream token: {}", e.getMessage());
                break;
            }
        }
    }

    private String buildToolSummary(List<ToolDefinition> tools) {
        return tools.stream()
                .map(t -> t.function().name().replace("_", " "))
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    private String truncateForEvent(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private List<JsonNode> loadAvailableTools(String categoryId) {
        if (categoryId != null && !categoryId.isBlank()) {
            return toolRegistryClient.getToolsByCategory(categoryId);
        }
        return toolRegistryClient.getAllTools();
    }

    private String resolveFunctionName(String toolId, String name) {
        if (toolId != null && !toolId.isBlank() && !toolId.matches("\\d+")) {
            return toolId;
        }
        if (name != null && !name.isBlank()) {
            return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
        }
        return toolId;
    }

    private Map<String, String> buildNameToToolIdMap(List<JsonNode> tools) {
        Map<String, String> map = new HashMap<>();
        for (JsonNode tool : tools) {
            String toolId = tool.has("toolId") ? tool.get("toolId").asText() : "";
            String name = tool.has("name") ? tool.get("name").asText() : toolId;
            String functionName = resolveFunctionName(toolId, name);
            map.put(functionName, toolId);
        }
        return map;
    }

    private List<ToolDefinition> buildToolDefinitions(List<JsonNode> tools) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (JsonNode tool : tools) {
            String toolId = tool.has("toolId") ? tool.get("toolId").asText() : "";
            String name = tool.has("name") ? tool.get("name").asText() : toolId;
            name = resolveFunctionName(toolId, name);
            String description = tool.has("humanReadableDescription") && !tool.get("humanReadableDescription").isNull()
                    ? tool.get("humanReadableDescription").asText()
                    : (tool.has("description") ? tool.get("description").asText() : "No description");

            ObjectNode parametersSchema = objectMapper.createObjectNode();
            parametersSchema.put("type", "object");
            ObjectNode properties = parametersSchema.putObject("properties");
            ArrayNode required = parametersSchema.putArray("required");

            if (tool.has("parameters") && tool.get("parameters").isArray()) {
                for (JsonNode param : tool.get("parameters")) {
                    String paramName = param.get("name").asText();
                    String paramType = param.has("type") ? param.get("type").asText() : "string";
                    boolean isRequired = param.has("required") && param.get("required").asBoolean();

                    ObjectNode prop = properties.putObject(paramName);
                    prop.put("type", paramType);
                    if (param.has("humanReadableDescription") && !param.get("humanReadableDescription").isNull()) {
                        prop.put("description", param.get("humanReadableDescription").asText());
                    } else if (param.has("description")) {
                        prop.put("description", param.get("description").asText());
                    }

                    if (isRequired) {
                        required.add(paramName);
                    }
                }
            }

            defs.add(ToolDefinition.function(name, description, parametersSchema));
        }
        return defs;
    }

    private String executeTool(String toolName, String argsJson, String categoryId) {
        try {
            Map<String, Object> parameters = new HashMap<>();
            if (argsJson != null && !argsJson.isBlank()) {
                parameters = objectMapper.readValue(argsJson, new TypeReference<>() {});
            }

            if (toolName.startsWith("rag_query") && categoryId != null) {
                parameters.put("category", categoryId);
            }

            JsonNode result = mcpGatewayClient.executeTool(toolName, parameters);
            if (result == null) return "ERROR: Tool execution returned no result";

            if (result.has("content") && result.get("content").isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : result.get("content")) {
                    if (item.has("text")) {
                        if (!sb.isEmpty()) sb.append("\n");
                        sb.append(item.get("text").asText());
                    }
                }
                if (!sb.isEmpty()) return sb.toString();
            }
            return result.toString();
        } catch (Exception e) {
            log.error("[AGENT-STREAM] Tool execution failed for {}: {}", toolName, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Build the system prompt for an agent config.
     * If systemPromptOverride is set, use it entirely.
     * Otherwise, append the agent's role as a persona section to the base prompt.
     */
    private String buildSystemPrompt(AgentConfig agentConfig) {
        if (agentConfig.getSystemPromptOverride() != null && !agentConfig.getSystemPromptOverride().isBlank()) {
            log.info("[AGENT-STREAM] Using system prompt override for agent {}", agentConfig.getAgentId());
            return agentConfig.getSystemPromptOverride() + "\n\n" + currentDateContext();
        }

        String basePrompt = AgentExecutor.DEFAULT_SYSTEM_PROMPT + "\n\n" + currentDateContext();
        if (agentConfig.getRole() != null && !agentConfig.getRole().isBlank()) {
            return basePrompt + "\n\n## Your Persona\n" + agentConfig.getRole();
        }
        return basePrompt;
    }

    /**
     * Inject prior conversation context from a previous session so the LLM
     * has multi-turn awareness (e.g. knows which app was discussed before).
     * Adds the previous user message + assistant answer as conversation turns.
     */
    /**
     * Build context prefix from the reply-to conversation context sent by the chat-app.
     * Contains the referenced Q&A pair and optional session summary.
     */
    private String buildContextFromReplyTo(List<com.naagi.orchestrator.model.OrchestrationRequest.ConversationContext> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CONVERSATION CONTEXT (the user is replying to a previous message) ===\n");
        for (var entry : context) {
            sb.append("[").append(entry.getRole()).append("]: ").append(entry.getContent()).append("\n");
        }
        sb.append("=== END CONTEXT ===\n\n");
        sb.append("IMPORTANT: The user's question below references the conversation above. ");
        sb.append("When the user says \"this application\", \"that job\", \"it\", etc., resolve these ");
        sb.append("to the ACTUAL names/IDs from the context above. Use the concrete values ");
        sb.append("(e.g. actual application names, job IDs, container names) in your tool calls, ");
        sb.append("NOT the pronouns.\n\n");
        sb.append("Also resolve relative time references like \"this month\", \"today\", \"last week\", ");
        sb.append("\"yesterday\" into ACTUAL date ranges using the current date from the system prompt. ");
        sb.append("For example, \"this month\" should become startTime/endTime parameters spanning the current calendar month. ");
        sb.append("Never pass relative time phrases as literal query text.\n\n");
        sb.append("User question: ");
        return sb.toString();
    }

    /**
     * Extract context from a previous session and return it as a prefix for the current message.
     * Returns null if no usable context found.
     */
    private String extractConversationContext(String previousSessionId) {
        if (previousSessionId == null || previousSessionId.isBlank()) return null;
        try {
            Optional<AgentSession> prev = sessionRepository.findBySessionId(previousSessionId);
            if (prev.isPresent() && prev.get().getFinalAnswer() != null) {
                AgentSession prevSession = prev.get();
                log.info("[AGENT-STREAM] Found previous session {} for context", previousSessionId);
                return "Previous question: \"" + prevSession.getUserMessage() + "\"\n"
                        + "Previous answer (summary): \"" + truncate(prevSession.getFinalAnswer(), 500) + "\"\n\n"
                        + "The user is continuing the conversation. Unless they mention a different application or entity, "
                        + "scope all tool calls to the same context as the previous question.\n\n"
                        + "Current question: ";
            }
        } catch (Exception e) {
            log.warn("[AGENT-STREAM] Failed to load previous session {}: {}", previousSessionId, e.getMessage());
        }
        return null;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    /**
     * @deprecated Use extractConversationContext instead
     */
    /**
     * Provide current date/time context so the LLM knows "today" and can
     * default time ranges to the current period instead of guessing.
     */
    private String currentDateContext() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return "## Current Date\nToday is " + today + ". "
                + "When the user does not specify a time range, default to the last 24 hours "
                + "(from " + today.minusDays(1) + "T00:00:00Z to " + today + "T23:59:59Z). "
                + "Never use arbitrary years like 2022 or 2023.";
    }

    /**
     * Filter loaded tools to only those assigned to the agent.
     * Matches by toolId field from the registry.
     */
    private List<JsonNode> filterToolsByAgent(List<JsonNode> allTools, AgentConfig agentConfig) {
        Set<String> agentToolIds = new HashSet<>();
        for (AgentConfig.AgentToolConfig tc : agentConfig.getTools()) {
            if (tc.getToolId() != null) {
                agentToolIds.add(tc.getToolId());
            }
        }

        List<JsonNode> filtered = new ArrayList<>();
        for (JsonNode tool : allTools) {
            String toolId = tool.has("toolId") ? tool.get("toolId").asText() : "";
            if (agentToolIds.contains(toolId)) {
                filtered.add(tool);
            }
        }

        log.info("[AGENT-STREAM] Filtered tools for agent {}: {} of {} available",
                agentConfig.getAgentId(), filtered.size(), allTools.size());

        // If no tools matched (misconfiguration), fall back to all tools
        if (filtered.isEmpty()) {
            log.warn("[AGENT-STREAM] No tools matched agent config — falling back to all {} tools", allTools.size());
            return allTools;
        }
        return filtered;
    }

    /**
     * Override tool descriptions with agent-specific custom descriptions where configured.
     */
    private List<ToolDefinition> applyCustomDescriptions(List<ToolDefinition> tools, AgentConfig agentConfig) {
        // Build map of toolId → customDescription
        Map<String, String> customDescriptions = new HashMap<>();
        for (AgentConfig.AgentToolConfig tc : agentConfig.getTools()) {
            if (tc.getToolId() != null && tc.getCustomDescription() != null && !tc.getCustomDescription().isBlank()) {
                customDescriptions.put(tc.getToolId(), tc.getCustomDescription());
            }
        }
        if (customDescriptions.isEmpty()) return tools;

        List<ToolDefinition> result = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            String name = tool.function().name();
            if (customDescriptions.containsKey(name)) {
                result.add(ToolDefinition.function(name, customDescriptions.get(name), tool.function().parameters()));
                log.debug("[AGENT-STREAM] Applied custom description for tool {}", name);
            } else {
                result.add(tool);
            }
        }
        return result;
    }

    /**
     * Detect and extract a tool call that the LLM embedded in its text response
     * instead of using native function calling. Matches JSON like:
     *   {"name": "tool_name", "parameters": {...}}
     */
    private ToolCall extractEmbeddedToolCall(String text, List<ToolDefinition> availableTools) {
        if (text == null || text.isBlank()) return null;
        try {
            // Build set of valid tool names
            Set<String> validNames = new HashSet<>();
            for (ToolDefinition tool : availableTools) {
                validNames.add(tool.function().name());
            }

            // Find JSON object in text
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) return null;

            String jsonCandidate = text.substring(start, end + 1);
            JsonNode node = objectMapper.readTree(jsonCandidate);

            // Check for {"name": "...", "parameters": {...}} pattern
            if (node.has("name") && node.has("parameters")) {
                String name = node.get("name").asText();
                if (validNames.contains(name)) {
                    String args = objectMapper.writeValueAsString(node.get("parameters"));
                    String callId = "embedded_" + UUID.randomUUID().toString().substring(0, 8);
                    return new ToolCall(callId, "function", new ToolCall.FunctionCall(name, args));
                }
            }
        } catch (Exception e) {
            log.debug("[AGENT-STREAM] No embedded tool call found in text: {}", e.getMessage());
        }
        return null;
    }
}
