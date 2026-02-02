package com.naagi.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.llm.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.function.Consumer;

@Service
@Slf4j
public class AgentExecutorService {

    private final LlamaCppProxy llamaCppProxy;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;

    @Value("${naagi.agent.max-steps:10}")
    private int defaultMaxSteps;

    @Value("${naagi.agent.default-system-prompt:You are an intelligent assistant with access to tools.}")
    private String defaultSystemPrompt;

    public AgentExecutorService(LlamaCppProxy llamaCppProxy,
                                ToolExecutionService toolExecutionService,
                                ObjectMapper objectMapper) {
        this.llamaCppProxy = llamaCppProxy;
        this.toolExecutionService = toolExecutionService;
        this.objectMapper = objectMapper;
    }

    // ==================== Non-Streaming Agent ====================

    public AgentResponse execute(AgentRequest request) {
        int maxSteps = request.maxSteps() != null ? request.maxSteps() : defaultMaxSteps;
        double temperature = request.temperature() != null ? request.temperature() : 0.2;
        int maxTokens = request.maxTokens() != null ? request.maxTokens() : 1024;

        String systemPrompt = request.systemPrompt() != null ? request.systemPrompt() : defaultSystemPrompt;

        // Build tool definitions and endpoint map
        List<ToolDefinition> toolDefs = new ArrayList<>();
        Map<String, AgentRequest.AgentTool> toolMap = new HashMap<>();
        for (AgentRequest.AgentTool tool : request.tools()) {
            toolDefs.add(ToolDefinition.function(tool.name(), tool.description(), tool.parameters()));
            toolMap.put(tool.name(), tool);
        }

        // Initialize conversation
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(request.message()));

        List<AgentResponse.AgentStepResult> steps = new ArrayList<>();
        Set<String> seenToolCalls = new HashSet<>();
        int toolCallCount = 0;

        for (int step = 0; step < maxSteps; step++) {
            ChatRequest chatRequest = ChatRequest.withTools(messages, toolDefs, "auto", temperature, maxTokens);
            ChatResponse response = llamaCppProxy.chat(chatRequest);

            if (response.hasToolCalls()) {
                messages.add(ChatMessage.assistantWithToolCalls(response.toolCalls()));

                for (ToolCall toolCall : response.toolCalls()) {
                    String toolName = toolCall.function().name();
                    String argsJson = toolCall.function().arguments();

                    // Loop detection
                    String callSignature = toolName + ":" + argsJson;
                    if (seenToolCalls.contains(callSignature)) {
                        messages.add(ChatMessage.tool(
                                "ERROR: Loop detected: same tool called with same arguments",
                                toolCall.id(), toolName));
                        continue;
                    }
                    seenToolCalls.add(callSignature);

                    // Execute tool
                    long toolStart = System.currentTimeMillis();
                    AgentRequest.AgentTool agentTool = toolMap.get(toolName);
                    String toolResult;
                    if (agentTool != null && agentTool.endpoint() != null) {
                        toolResult = toolExecutionService.executeTool(
                                agentTool.endpoint().url(),
                                agentTool.endpoint().method(),
                                argsJson);
                    } else {
                        toolResult = "ERROR: No endpoint configured for tool: " + toolName;
                    }
                    long toolDuration = System.currentTimeMillis() - toolStart;

                    messages.add(ChatMessage.tool(toolResult, toolCall.id(), toolName));
                    steps.add(new AgentResponse.AgentStepResult(toolName, argsJson, toolResult, toolDuration));
                    toolCallCount++;
                }
            } else {
                // Check for embedded tool calls
                String answer = response.content();
                ToolCall embeddedCall = extractEmbeddedToolCall(answer, toolDefs);
                if (embeddedCall != null) {
                    log.info("[AGENT] Detected embedded tool call: {}", embeddedCall.function().name());
                    messages.add(ChatMessage.assistantWithToolCalls(List.of(embeddedCall)));

                    String toolName = embeddedCall.function().name();
                    String argsJson = embeddedCall.function().arguments();

                    String callSignature = toolName + ":" + argsJson;
                    if (seenToolCalls.contains(callSignature)) {
                        messages.add(ChatMessage.tool(
                                "ERROR: Loop detected", embeddedCall.id(), toolName));
                        continue;
                    }
                    seenToolCalls.add(callSignature);

                    long toolStart = System.currentTimeMillis();
                    AgentRequest.AgentTool agentTool = toolMap.get(toolName);
                    String toolResult;
                    if (agentTool != null && agentTool.endpoint() != null) {
                        toolResult = toolExecutionService.executeTool(
                                agentTool.endpoint().url(),
                                agentTool.endpoint().method(),
                                argsJson);
                    } else {
                        toolResult = "ERROR: No endpoint configured for tool: " + toolName;
                    }
                    long toolDuration = System.currentTimeMillis() - toolStart;

                    messages.add(ChatMessage.tool(toolResult, embeddedCall.id(), toolName));
                    steps.add(new AgentResponse.AgentStepResult(toolName, argsJson, toolResult, toolDuration));
                    toolCallCount++;
                    continue;
                }

                // Final answer
                return new AgentResponse(answer, steps, step + 1, toolCallCount);
            }
        }

        // Max steps reached
        return new AgentResponse(
                "Reached maximum steps (" + maxSteps + "). Partial results may be incomplete.",
                steps, maxSteps, toolCallCount);
    }

    // ==================== Streaming Agent ====================

    public void executeStream(AgentRequest request, SseEmitter emitter) {
        int maxSteps = request.maxSteps() != null ? request.maxSteps() : defaultMaxSteps;
        double temperature = request.temperature() != null ? request.temperature() : 0.2;
        int maxTokens = request.maxTokens() != null ? request.maxTokens() : 1024;

        String systemPrompt = request.systemPrompt() != null ? request.systemPrompt() : defaultSystemPrompt;

        List<ToolDefinition> toolDefs = new ArrayList<>();
        Map<String, AgentRequest.AgentTool> toolMap = new HashMap<>();
        for (AgentRequest.AgentTool tool : request.tools()) {
            toolDefs.add(ToolDefinition.function(tool.name(), tool.description(), tool.parameters()));
            toolMap.put(tool.name(), tool);
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(request.message()));

        Set<String> seenToolCalls = new HashSet<>();
        int toolCallCount = 0;

        try {
            sendEvent(emitter, "session_start", Map.of("toolCount", toolDefs.size()));

            for (int step = 0; step < maxSteps; step++) {
                ChatRequest chatRequest = ChatRequest.withTools(messages, toolDefs, "auto", temperature, maxTokens);
                ChatResponse response = llamaCppProxy.chat(chatRequest);

                if (response.hasToolCalls()) {
                    messages.add(ChatMessage.assistantWithToolCalls(response.toolCalls()));

                    for (ToolCall toolCall : response.toolCalls()) {
                        String toolName = toolCall.function().name();
                        String argsJson = toolCall.function().arguments();

                        sendEvent(emitter, "agent_step", Map.of(
                                "step", step + 1,
                                "type", "tool_call",
                                "tool", toolName,
                                "args", argsJson != null ? argsJson : "{}"));

                        String callSignature = toolName + ":" + argsJson;
                        if (seenToolCalls.contains(callSignature)) {
                            messages.add(ChatMessage.tool(
                                    "ERROR: Loop detected", toolCall.id(), toolName));
                            sendEvent(emitter, "tool_result", Map.of(
                                    "step", step + 1, "tool", toolName, "error", "Loop detected"));
                            continue;
                        }
                        seenToolCalls.add(callSignature);

                        long toolStart = System.currentTimeMillis();
                        AgentRequest.AgentTool agentTool = toolMap.get(toolName);
                        String toolResult;
                        if (agentTool != null && agentTool.endpoint() != null) {
                            toolResult = toolExecutionService.executeTool(
                                    agentTool.endpoint().url(),
                                    agentTool.endpoint().method(),
                                    argsJson);
                        } else {
                            toolResult = "ERROR: No endpoint configured for tool: " + toolName;
                        }
                        long toolDuration = System.currentTimeMillis() - toolStart;

                        messages.add(ChatMessage.tool(toolResult, toolCall.id(), toolName));
                        toolCallCount++;

                        sendEvent(emitter, "tool_result", Map.of(
                                "step", step + 1,
                                "tool", toolName,
                                "durationMs", toolDuration,
                                "result", truncate(toolResult, 500)));
                    }
                } else {
                    String answer = response.content();

                    // Check for embedded tool calls
                    ToolCall embeddedCall = extractEmbeddedToolCall(answer, toolDefs);
                    if (embeddedCall != null) {
                        messages.add(ChatMessage.assistantWithToolCalls(List.of(embeddedCall)));
                        String toolName = embeddedCall.function().name();
                        String argsJson = embeddedCall.function().arguments();

                        sendEvent(emitter, "agent_step", Map.of(
                                "step", step + 1, "type", "tool_call",
                                "tool", toolName, "args", argsJson != null ? argsJson : "{}"));

                        String callSignature = toolName + ":" + argsJson;
                        if (seenToolCalls.contains(callSignature)) {
                            messages.add(ChatMessage.tool("ERROR: Loop detected", embeddedCall.id(), toolName));
                            continue;
                        }
                        seenToolCalls.add(callSignature);

                        long toolStart = System.currentTimeMillis();
                        AgentRequest.AgentTool agentTool = toolMap.get(toolName);
                        String toolResult;
                        if (agentTool != null && agentTool.endpoint() != null) {
                            toolResult = toolExecutionService.executeTool(
                                    agentTool.endpoint().url(), agentTool.endpoint().method(), argsJson);
                        } else {
                            toolResult = "ERROR: No endpoint for tool: " + toolName;
                        }
                        long toolDuration = System.currentTimeMillis() - toolStart;

                        messages.add(ChatMessage.tool(toolResult, embeddedCall.id(), toolName));
                        toolCallCount++;

                        sendEvent(emitter, "tool_result", Map.of(
                                "step", step + 1, "tool", toolName,
                                "durationMs", toolDuration, "result", truncate(toolResult, 500)));
                        continue;
                    }

                    // Stream final answer token by token
                    if (answer != null && !answer.isBlank()) {
                        String[] words = answer.split("(?<=\\s)");
                        for (String word : words) {
                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data(objectMapper.writeValueAsString(Map.of("t", word))));
                        }
                    }

                    sendEvent(emitter, "done", Map.of(
                            "totalSteps", step + 1,
                            "totalToolCalls", toolCallCount));
                    emitter.complete();
                    return;
                }
            }

            // Max steps reached
            sendEvent(emitter, "done", Map.of(
                    "totalSteps", maxSteps,
                    "totalToolCalls", toolCallCount,
                    "maxStepsReached", true));
            emitter.complete();

        } catch (Exception e) {
            log.error("[AGENT] Stream execution failed", e);
            try {
                sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    // ==================== Helpers ====================

    private void sendEvent(SseEmitter emitter, String name, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(name)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.warn("[AGENT] Failed to send SSE event {}: {}", name, e.getMessage());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private ToolCall extractEmbeddedToolCall(String text, List<ToolDefinition> availableTools) {
        if (text == null || text.isBlank()) return null;
        try {
            Set<String> validNames = new HashSet<>();
            for (ToolDefinition tool : availableTools) {
                validNames.add(tool.function().name());
            }

            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) return null;

            String jsonCandidate = text.substring(start, end + 1);
            JsonNode node = objectMapper.readTree(jsonCandidate);

            if (node.has("name") && node.has("parameters")) {
                String name = node.get("name").asText();
                if (validNames.contains(name)) {
                    String args = objectMapper.writeValueAsString(node.get("parameters"));
                    String callId = "embedded_" + UUID.randomUUID().toString().substring(0, 8);
                    return new ToolCall(callId, "function", new ToolCall.FunctionCall(name, args));
                }
            }
        } catch (Exception e) {
            log.debug("[AGENT] No embedded tool call found: {}", e.getMessage());
        }
        return null;
    }
}
