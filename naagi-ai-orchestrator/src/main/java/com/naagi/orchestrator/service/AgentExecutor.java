package com.naagi.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naagi.orchestrator.entity.AgentSession;
import com.naagi.orchestrator.entity.AgentStep;
import com.naagi.orchestrator.llm.*;
import com.naagi.orchestrator.repository.AgentSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class AgentExecutor {

    private final LlmClient llmClient;
    private final McpGatewayClient mcpGatewayClient;
    private final ToolRegistryClient toolRegistryClient;
    private final AgentSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    @Value("${naagi.agent.max-steps:10}")
    private int maxSteps;

    @Value("${naagi.agent.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${naagi.agent.parallel-tool-calls:true}")
    private boolean parallelToolCalls;

    @Value("${naagi.agent.planning.enabled:false}")
    private boolean planningEnabled;

    @Value("${naagi.agent.reflection.enabled:false}")
    private boolean reflectionEnabled;

    public AgentExecutor(LlmClient llmClient,
                         McpGatewayClient mcpGatewayClient,
                         ToolRegistryClient toolRegistryClient,
                         AgentSessionRepository sessionRepository,
                         ObjectMapper objectMapper,
                         @Value("${naagi.agent.system-prompt:classpath:agent-system-prompt.txt}") String systemPromptPath) {
        this.llmClient = llmClient;
        this.mcpGatewayClient = mcpGatewayClient;
        this.toolRegistryClient = toolRegistryClient;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadSystemPrompt(systemPromptPath);
    }

    public AgentSession execute(String userMessage, String categoryId) {
        AgentSession session = new AgentSession(userMessage, categoryId);
        long startTime = System.currentTimeMillis();

        try {
            // Load available tools and build name→toolId mapping
            List<JsonNode> availableToolsJson = loadAvailableTools(categoryId);
            Map<String, String> nameToToolId = buildNameToToolIdMap(availableToolsJson);
            List<ToolDefinition> tools = buildToolDefinitions(availableToolsJson);

            log.info("[AGENT] Starting session {} with {} available tools, maxSteps={}",
                    session.getSessionId(), tools.size(), maxSteps);

            // Initialize conversation
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.user(userMessage));

            // Planning phase: ask LLM to create a step-by-step plan before execution
            String plan = null;
            if (planningEnabled && tools.size() > 1) {
                plan = generatePlan(messages, tools);
                if (plan != null) {
                    session.addStep(AgentStep.plan(0, plan));
                    log.info("[AGENT] Plan generated for session {}: {}", session.getSessionId(),
                            plan.substring(0, Math.min(200, plan.length())));
                    // Inject the plan into the conversation so the agent follows it
                    messages.add(ChatMessage.assistant(plan));
                    messages.add(ChatMessage.user(
                            "Good plan. Now execute it step by step using the available tools. "
                            + "Start with the first step."));
                }
            }

            // Track tool call pairs for loop detection
            Set<String> seenToolCalls = new HashSet<>();
            int toolCallCount = 0;

            // ReAct loop
            for (int step = 0; step < maxSteps; step++) {
                // Check timeout
                if (System.currentTimeMillis() - startTime > timeoutSeconds * 1000L) {
                    log.warn("[AGENT] Session {} timed out after {}s", session.getSessionId(), timeoutSeconds);
                    session.fail("Agent timed out after " + timeoutSeconds + " seconds");
                    break;
                }

                // Call LLM with tools
                ChatRequest request = ChatRequest.withTools(messages, tools, "auto", 0.2, 1024);
                ChatResponse response = llmClient.chat(request);

                if (response.hasToolCalls()) {
                    // ACT: LLM wants to call tools
                    messages.add(ChatMessage.assistantWithToolCalls(response.toolCalls()));

                    // Separate calls into executable vs loop-detected
                    List<ToolCall> executableCalls = new ArrayList<>();
                    for (ToolCall toolCall : response.toolCalls()) {
                        String callSignature = toolCall.function().name() + ":" + toolCall.function().arguments();
                        if (seenToolCalls.contains(callSignature)) {
                            log.warn("[AGENT] Loop detected: {} called with same args twice", toolCall.function().name());
                            messages.add(ChatMessage.tool(
                                    "ERROR: You already called this tool with the same arguments. "
                                            + "Use the previous result or try a different approach.",
                                    toolCall.id(), toolCall.function().name()));
                        } else {
                            seenToolCalls.add(callSignature);
                            executableCalls.add(toolCall);
                        }
                    }

                    if (parallelToolCalls && executableCalls.size() > 1) {
                        // Parallel execution for multiple independent tool calls
                        log.info("[AGENT] Step {} - Executing {} tools in parallel", step + 1, executableCalls.size());

                        record ToolResult(ToolCall call, String result, long durationMs) {}

                        List<CompletableFuture<ToolResult>> futures = executableCalls.stream()
                                .map(toolCall -> CompletableFuture.supplyAsync(() -> {
                                    long toolStart = System.currentTimeMillis();
                                    String registryToolId = nameToToolId.getOrDefault(toolCall.function().name(), toolCall.function().name());
                                    String toolResult = executeTool(registryToolId, toolCall.function().arguments(), categoryId);
                                    long toolDuration = System.currentTimeMillis() - toolStart;
                                    return new ToolResult(toolCall, toolResult, toolDuration);
                                }))
                                .toList();

                        // Wait for all to complete
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                        for (CompletableFuture<ToolResult> future : futures) {
                            ToolResult tr = future.join();
                            String toolName = tr.call().function().name();
                            log.info("[AGENT] Step {} - Tool: {} ({}ms, parallel)", step + 1, toolName, tr.durationMs());
                            messages.add(ChatMessage.tool(tr.result(), tr.call().id(), toolName));
                            session.addStep(AgentStep.toolCall(step + 1, toolName, tr.call().function().arguments(), tr.result(), tr.durationMs()));
                            toolCallCount++;
                        }
                    } else {
                        // Sequential execution (single call or parallel disabled)
                        for (ToolCall toolCall : executableCalls) {
                            String toolName = toolCall.function().name();
                            String argsJson = toolCall.function().arguments();

                            long toolStart = System.currentTimeMillis();
                            String registryToolId = nameToToolId.getOrDefault(toolName, toolName);
                            String toolResult = executeTool(registryToolId, argsJson, categoryId);
                            long toolDuration = System.currentTimeMillis() - toolStart;

                            log.info("[AGENT] Step {} - Tool: {} ({}ms)", step + 1, toolName, toolDuration);
                            messages.add(ChatMessage.tool(toolResult, toolCall.id(), toolName));
                            session.addStep(AgentStep.toolCall(step + 1, toolName, argsJson, toolResult, toolDuration));
                            toolCallCount++;
                        }
                    }
                    // Self-reflection: evaluate tool results quality
                    if (reflectionEnabled && toolCallCount > 0 && toolCallCount % 2 == 0) {
                        String reflection = reflect(messages, userMessage);
                        if (reflection != null) {
                            session.addStep(AgentStep.reflection(step + 1, reflection));
                            log.debug("[AGENT] Reflection at step {}: {}", step + 1,
                                    reflection.substring(0, Math.min(150, reflection.length())));
                        }
                    }

                } else {
                    // DONE: LLM returned final answer
                    String answer = response.content();

                    log.info("[AGENT] Session {} completed in {} steps with {} tool calls",
                            session.getSessionId(), step + 1, toolCallCount);

                    session.addStep(AgentStep.finalAnswer(step + 1, answer));
                    session.setTotalToolCalls(toolCallCount);
                    session.complete(answer);
                    break;
                }
            }

            // If we exhausted max steps without a final answer
            if (!session.isCompleted() && session.getFinalAnswer() == null) {
                String fallback = synthesizeFallbackAnswer(messages);
                session.setTotalToolCalls(toolCallCount);
                session.maxStepsReached(fallback);
                log.warn("[AGENT] Session {} reached max steps ({})", session.getSessionId(), maxSteps);
            }

        } catch (Exception e) {
            log.error("[AGENT] Session {} failed", session.getSessionId(), e);
            session.fail("Agent execution failed: " + e.getMessage());
        }

        return sessionRepository.save(session);
    }

    private List<JsonNode> loadAvailableTools(String categoryId) {
        if (categoryId != null && !categoryId.isBlank()) {
            return toolRegistryClient.getToolsByCategory(categoryId);
        }
        return toolRegistryClient.getAllTools();
    }

    /**
     * Resolve a descriptive function name for the LLM.
     * If toolId is numeric (auto-generated), use the name field instead.
     */
    private String resolveFunctionName(String toolId, String name) {
        if (toolId != null && !toolId.isBlank() && !toolId.matches("\\d+")) {
            return toolId;
        }
        if (name != null && !name.isBlank()) {
            // Convert spaces/special chars to underscores for valid function names
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
                    if (param.has("description")) {
                        prop.put("description", param.get("description").asText());
                    }
                    if (param.has("humanReadableDescription") && !param.get("humanReadableDescription").isNull()) {
                        prop.put("description", param.get("humanReadableDescription").asText());
                    }
                    if (param.has("enumValues") && !param.get("enumValues").isNull()) {
                        String enumStr = param.get("enumValues").asText();
                        if (!enumStr.isBlank()) {
                            ArrayNode enumArr = prop.putArray("enum");
                            for (String val : enumStr.split(",")) {
                                enumArr.add(val.trim());
                            }
                        }
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

            // Inject category for RAG queries
            if (toolName.startsWith("rag_query") && categoryId != null) {
                parameters.put("category", categoryId);
            }

            JsonNode result = mcpGatewayClient.executeTool(toolName, parameters);

            if (result == null) {
                return "ERROR: Tool execution returned no result";
            }

            // Extract text content from MCP response format
            return extractToolResultText(result);

        } catch (Exception e) {
            log.error("[AGENT] Tool execution failed for {}: {}", toolName, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private String extractToolResultText(JsonNode result) {
        // MCP format: { "content": [{ "type": "text", "text": "..." }] }
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

        // Fallback: return raw JSON
        return result.toString();
    }

    private String synthesizeFallbackAnswer(List<ChatMessage> messages) {
        try {
            messages.add(ChatMessage.user(
                    "You have reached the maximum number of tool-calling steps. "
                            + "Based on all the information you have gathered so far, "
                            + "provide the best possible answer to the original question. "
                            + "If some information is missing, note what could not be retrieved."));

            ChatRequest request = ChatRequest.of(messages, 0.2, 1024);
            ChatResponse response = llmClient.chat(request);
            return response.content();
        } catch (Exception e) {
            log.error("[AGENT] Failed to synthesize fallback answer", e);
            return "I was unable to complete the task within the allowed number of steps. "
                    + "Some information may have been gathered but the answer is incomplete.";
        }
    }

    private String loadSystemPrompt(String path) {
        try {
            if (path.startsWith("classpath:")) {
                String resourcePath = path.substring("classpath:".length());
                ClassPathResource resource = new ClassPathResource(resourcePath);
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
            return path;
        } catch (Exception e) {
            log.warn("Could not load system prompt from {}, using default", path);
            return DEFAULT_SYSTEM_PROMPT;
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
            log.warn("[AGENT] Planning phase failed, proceeding without plan: {}", e.getMessage());
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
                // If reflection suggests adjustment, inject it as guidance
                String lower = reflection.toLowerCase();
                if (!lower.contains("on track") && !lower.contains("looks good")
                        && !lower.contains("sufficient")) {
                    messages.add(ChatMessage.user(
                            "Reflection: " + reflection + " — Adjust your next action accordingly."));
                }
                return reflection.trim();
            }
        } catch (Exception e) {
            log.warn("[AGENT] Reflection failed: {}", e.getMessage());
        }
        return null;
    }

    static final String DEFAULT_SYSTEM_PROMPT = """
            You are an intelligent assistant with access to tools. Your job is to answer the user's \
            question by gathering information through available tools.

            RULES:
            1. Break complex questions into steps. Call multiple tools as needed to gather all required data.
            2. After receiving a tool result, decide silently whether to call another tool or provide the final answer. \
            Do NOT narrate your reasoning, do NOT describe what you learned from the tool, and do NOT explain what you plan to do next. \
            Simply call the next tool or provide the final answer.
            3. When you have ALL the information needed, provide ONLY the final answer. Do NOT prefix it with phrases like \
            "Based on the tool result" or "The final answer is". Just present the information directly.
            4. If a tool returns an error, briefly state the limitation.
            5. Never invent data. Only use information returned by tools.
            6. Keep your final answer well-structured, concise, and easy to read. Use bullet points or numbered lists for multiple items. \
            IMPORTANT: Do NOT dump raw log entries or repeat every line from tool results verbatim. Summarize the key information \
            (e.g. job name, status, duration, key metrics). Only include individual log lines if the user specifically asks for logs or details.
            7. IMPORTANT: Before calling any tool, verify it is relevant to the user's question. If NONE of the \
            available tools can answer the question, do NOT call any tool. Instead respond with: \
            "Sorry, this capability is not available in the current category." and briefly describe what the available tools do.
            8. Never force-fit a tool to a question it was not designed for.
            9. IMPORTANT: If you called a tool and it returned empty results or no data, that means no matching \
            records were found - NOT that the capability is unavailable. Tell the user clearly that no results were \
            found. Never say "this capability is not available" after you have already called a tool.
            10. CONVERSATION CONTEXT: If the conversation history shows a previous question about a specific \
            application, service, or entity, and the user's follow-up question does not mention a different one, \
            assume the follow-up is about THE SAME application/service/entity. For example, if the previous question \
            was about "app-user-mgmt" and the follow-up is "are there any failures?", scope your search to app-user-mgmt.""";
}
