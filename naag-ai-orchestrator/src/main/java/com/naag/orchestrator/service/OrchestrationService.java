package com.naag.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naag.orchestrator.llm.LlmClient;
import com.naag.orchestrator.metrics.OrchestratorMetrics;
import com.naag.orchestrator.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrchestrationService {

    private final ToolSelectionService toolSelectionService;
    private final ToolRegistryClient toolRegistryClient;
    private final McpGatewayClient mcpGatewayClient;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final OrchestratorMetrics metrics;

    public OrchestrationService(ToolSelectionService toolSelectionService,
                                ToolRegistryClient toolRegistryClient,
                                McpGatewayClient mcpGatewayClient,
                                LlmClient llmClient,
                                ObjectMapper objectMapper,
                                OrchestratorMetrics metrics) {
        this.toolSelectionService = toolSelectionService;
        this.toolRegistryClient = toolRegistryClient;
        this.mcpGatewayClient = mcpGatewayClient;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public OrchestrationResponse orchestrate(OrchestrationRequest request) {
        long orchestrationStart = System.currentTimeMillis();
        log.info("Orchestrating request: {} (category: {})", request.getMessage(), request.getCategoryId());

        try {
            // Get available tools from registry - prefer category-specific tools
            List<JsonNode> availableTools;
            if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
                availableTools = toolRegistryClient.getToolsByCategory(request.getCategoryId());
                log.info("Loaded {} tools for category {}", availableTools.size(), request.getCategoryId());
            } else {
                availableTools = toolRegistryClient.getAllTools();
                log.info("Loaded {} tools (all categories)", availableTools.size());
            }

            if (availableTools.isEmpty()) {
                return OrchestrationResponse.builder()
                        .intent(Intent.UNKNOWN)
                        .response("No tools available for this category. Please ensure tools are registered.")
                        .build();
            }

            // Use LLM to select tool and extract parameters
            long selectionStart = System.currentTimeMillis();
            ToolSelectionResult selection = toolSelectionService.selectTool(request.getMessage(), availableTools);
            long selectionTime = System.currentTimeMillis() - selectionStart;
            metrics.recordToolSelectionTime(selectionTime);

            log.info("Tool selection result: tool={}, confidence={}", selection.getSelectedTool(), selection.getConfidence());

            // Record confidence metrics
            boolean isHigh = toolSelectionService.isHighConfidence(selection.getConfidence());
            boolean isLow = toolSelectionService.isLowConfidence(selection.getConfidence());
            metrics.recordConfidence(selection.getConfidence(), isHigh, isLow);

            OrchestrationResponse response;
            // Handle based on confidence level
            if (selection.getSelectedTool() == null || isLow) {
                response = handleLowConfidence(request, selection);
            } else if (!isHigh) {
                response = handleMediumConfidence(request, selection);
            } else {
                // High confidence - execute the tool
                response = executeToolAndRespond(request, selection);
            }

            long orchestrationTime = System.currentTimeMillis() - orchestrationStart;
            metrics.recordOrchestrationTime(orchestrationTime);
            log.info("[TIMING] Orchestration completed: {}ms (selection={}ms)", orchestrationTime, selectionTime);

            return response;
        } catch (Exception e) {
            metrics.recordOrchestrationError();
            throw e;
        }
    }

    private OrchestrationResponse handleLowConfidence(OrchestrationRequest request, ToolSelectionResult selection) {
        StringBuilder response = new StringBuilder();

        // Check if no tool was found at all
        if (selection.getSelectedTool() == null) {
            response.append("I don't have a tool available to handle this request.\n\n");
            if (selection.getReasoning() != null && !selection.getReasoning().isEmpty()) {
                response.append("**Reason:** ").append(selection.getReasoning()).append("\n\n");
            }
            response.append("This type of query may require a tool that hasn't been registered yet, ");
            response.append("or you could try rephrasing your question to search the knowledge base.");
        } else {
            response.append("I'm not sure which tool you want to use.");

            if (!selection.getAlternatives().isEmpty()) {
                response.append(" Did you mean:\n\n");
                int index = 1;
                for (AlternativeTool alt : selection.getAlternatives()) {
                    response.append(index++).append(". **").append(alt.getToolName())
                            .append("** - ").append(alt.getReasoning()).append("\n");
                }
            }

            response.append("\nPlease clarify which tool you'd like to use.");
        }

        return OrchestrationResponse.builder()
                .intent(Intent.CLARIFICATION_NEEDED)
                .confidence(selection.getConfidence())
                .reasoning(selection.getReasoning())
                .alternatives(selection.getAlternatives())
                .response(response.toString())
                .requiresConfirmation(true)
                .build();
    }

    private OrchestrationResponse handleMediumConfidence(OrchestrationRequest request, ToolSelectionResult selection) {
        String response = String.format(
                "I think you want to use **%s**\n\n" +
                        "Reasoning: %s\n\n" +
                        "Confidence: %.0f%%\n\n" +
                        "Should I proceed? (yes/no)",
                selection.getSelectedTool(),
                selection.getReasoning(),
                selection.getConfidence() * 100
        );

        return OrchestrationResponse.builder()
                .intent(Intent.TOOL_CALL)
                .selectedTool(selection.getSelectedTool())
                .confidence(selection.getConfidence())
                .parameters(selection.getExtractedParameters())
                .reasoning(selection.getReasoning())
                .alternatives(selection.getAlternatives())
                .response(response)
                .requiresConfirmation(true)
                .build();
    }

    private OrchestrationResponse executeToolAndRespond(OrchestrationRequest request, ToolSelectionResult selection) {
        long executionStart = System.currentTimeMillis();
        try {
            // Prepare parameters - inject categoryId for RAG queries
            Map<String, Object> parameters = new HashMap<>(selection.getExtractedParameters());
            String toolName = selection.getSelectedTool();

            // For RAG queries, always inject the category from the request
            // Note: RAG service expects "category" not "categoryId"
            if (toolName != null && toolName.startsWith("rag_query") && request.getCategoryId() != null) {
                parameters.put("category", request.getCategoryId());
                log.info("Injected category={} into RAG query parameters", request.getCategoryId());
            }

            // Apply locked parameter values from category overrides
            if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
                applyLockedParameters(request.getCategoryId(), toolName, parameters);
            }

            // Execute the tool via MCP gateway
            JsonNode toolResult = mcpGatewayClient.executeTool(
                    toolName,
                    parameters
            );

            long executionTime = System.currentTimeMillis() - executionStart;
            metrics.recordToolExecutionTime(executionTime);
            log.info("[TIMING] Tool execution ({}): {}ms", toolName, executionTime);

            // Format response based on tool type - pass user's question for natural language generation
            String response = formatToolResponse(request.getMessage(), selection, toolResult);

            return OrchestrationResponse.builder()
                    .intent(Intent.TOOL_CALL)
                    .selectedTool(selection.getSelectedTool())
                    .confidence(selection.getConfidence())
                    .parameters(selection.getExtractedParameters())
                    .toolResult(toolResult)
                    .reasoning(selection.getReasoning())
                    .response(response)
                    .build();
        } catch (Exception e) {
            log.error("Error executing tool", e);
            metrics.recordToolExecutionError();
            return OrchestrationResponse.builder()
                    .intent(Intent.TOOL_CALL)
                    .selectedTool(selection.getSelectedTool())
                    .confidence(selection.getConfidence())
                    .response("Failed to execute tool: " + e.getMessage())
                    .build();
        }
    }

    private String formatToolResponse(String userQuestion, ToolSelectionResult selection, JsonNode toolResult) {
        String toolName = selection.getSelectedTool();

        // Handle RAG query results specially
        if (toolName != null && toolName.startsWith("rag_query")) {
            return formatRagResponse(toolResult);
        }

        // For other tools, generate a natural language response using LLM
        return generateNaturalResponse(userQuestion, toolName, toolResult);
    }

    /**
     * Use LLM to generate a natural language response based on the tool result and user's question.
     */
    private String generateNaturalResponse(String userQuestion, String toolName, JsonNode toolResult) {
        if (toolResult == null) {
            return "The tool did not return any results.";
        }

        try {
            // Extract the actual data from MCP response wrapper
            String jsonData = extractToolData(toolResult);

            String prompt = String.format("""
                TASK: Answer a question using provided data. Output PLAIN TEXT only.

                CRITICAL: Do NOT output JSON, function calls, or tool invocations. This is NOT a tool selection task.

                Question: %s

                Data:
                %s

                Instructions:
                - Write a natural language answer in plain English
                - List the services/items found if the question asks about them
                - Be concise but complete
                - NO JSON output, NO function calls, NO code

                Plain text answer:""", userQuestion, jsonData);

            String response = llmClient.chat(prompt, 0.3, 256);
            return response != null ? response.trim() : "I found the data but couldn't format a response.";
        } catch (Exception e) {
            log.error("Error generating natural response", e);
            // Fallback to formatted JSON
            return String.format("**Result from %s:**\n```json\n%s\n```",
                    toolName, toolResult.toPrettyString());
        }
    }

    /**
     * Extract the actual data from MCP gateway response wrapper.
     */
    private String extractToolData(JsonNode toolResult) {
        try {
            // MCP gateway returns: { "content": [ { "type": "text", "text": "..." } ] }
            JsonNode contentArray = toolResult.get("content");
            if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                JsonNode firstContent = contentArray.get(0);
                if (firstContent.has("text")) {
                    return firstContent.get("text").asText();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract content from MCP wrapper", e);
        }
        // Return the raw JSON if not wrapped
        return toolResult.toPrettyString();
    }

    private String formatRagResponse(JsonNode toolResult) {
        if (toolResult == null) {
            return "I couldn't find an answer to your question in the knowledge base.";
        }

        try {
            // The MCP gateway returns: { "content": [ { "type": "text", "text": "..." } ] }
            JsonNode contentArray = toolResult.get("content");
            if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                JsonNode firstContent = contentArray.get(0);
                if (firstContent.has("text")) {
                    String textContent = firstContent.get("text").asText();
                    // Parse the inner JSON from RAG service
                    JsonNode ragResponse = objectMapper.readTree(textContent);
                    return formatRagResponseContent(ragResponse);
                }
            }

            // Fallback: try to parse toolResult directly as RAG response
            if (toolResult.has("answer")) {
                return formatRagResponseContent(toolResult);
            }

            return "I couldn't find an answer to your question in the knowledge base.";
        } catch (Exception e) {
            log.error("Error formatting RAG response", e);
            return "I found some information but had trouble formatting it. Raw result:\n" + toolResult.toPrettyString();
        }
    }

    private String formatRagResponseContent(JsonNode ragResponse) {
        StringBuilder sb = new StringBuilder();

        // Check for errors
        if (ragResponse.has("errorMessage") && !ragResponse.get("errorMessage").isNull()) {
            String error = ragResponse.get("errorMessage").asText();
            if (!error.isEmpty()) {
                return "Error searching knowledge base: " + error;
            }
        }

        // Get the answer
        String answer = "";
        if (ragResponse.has("answer") && !ragResponse.get("answer").isNull()) {
            answer = ragResponse.get("answer").asText();
            // Clean up the answer - remove extra quotes and escaping
            answer = cleanupAnswer(answer);
        }

        if (answer.isEmpty()) {
            return "I couldn't find a relevant answer in the knowledge base for your question.";
        }

        sb.append(answer);

        // Add sources section
        if (ragResponse.has("sources") && ragResponse.get("sources").isArray()) {
            JsonNode sources = ragResponse.get("sources");
            if (sources.size() > 0) {
                sb.append("\n\n---\n**Sources:**\n");
                int count = 0;
                java.util.Set<String> seenDocs = new java.util.HashSet<>();
                for (JsonNode source : sources) {
                    if (count >= 3) break; // Limit to top 3 unique sources
                    String docId = source.has("docId") ? source.get("docId").asText() : "Unknown";
                    // Deduplicate by docId
                    if (seenDocs.contains(docId)) continue;
                    seenDocs.add(docId);

                    double score = source.has("relevanceScore") ? source.get("relevanceScore").asDouble() : 0;
                    // Format doc ID for display - remove file extensions and clean up
                    String displayName = formatDocId(docId);
                    sb.append(String.format("- %s (%.0f%% match)\n", displayName, score * 100));
                    count++;
                }
            }
        }

        return sb.toString();
    }

    private String formatDocId(String docId) {
        if (docId == null) return "Unknown";
        // Remove common file extensions
        String name = docId.replaceAll("\\.(pdf|md|txt|doc|docx|html)$", "");
        // Convert hyphens and underscores to spaces
        name = name.replaceAll("[-_]", " ");
        // Title case
        return formatTitle(name);
    }

    private String cleanupAnswer(String answer) {
        if (answer == null) return "";

        // Remove leading/trailing quotes
        answer = answer.trim();
        if (answer.startsWith("\"") && answer.endsWith("\"")) {
            answer = answer.substring(1, answer.length() - 1);
        }

        // Unescape common sequences
        answer = answer.replace("\\n", "\n")
                       .replace("\\\"", "\"")
                       .replace("\\\\", "\\");

        // If it looks like JSON, try to extract meaningful content
        if (answer.trim().startsWith("{") && answer.trim().endsWith("}")) {
            try {
                JsonNode jsonAnswer = objectMapper.readTree(answer);
                return formatJsonAnswer(jsonAnswer);
            } catch (Exception e) {
                // Not valid JSON, return as-is
            }
        }

        return answer;
    }

    private String formatJsonAnswer(JsonNode jsonAnswer) {
        StringBuilder sb = new StringBuilder();

        // Handle common JSON structures from LLM responses

        // Case 1: { "name": "...", "parameters": {...} } - function-call style
        if (jsonAnswer.has("name") && jsonAnswer.has("parameters")) {
            String name = jsonAnswer.get("name").asText();
            sb.append("**").append(formatTitle(name)).append("**\n\n");

            JsonNode params = jsonAnswer.get("parameters");
            if (params.isObject() && params.size() > 0) {
                var fields = params.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    String key = formatTitle(field.getKey());
                    JsonNode value = field.getValue();

                    if (value.isArray()) {
                        sb.append("**").append(key).append(":**\n");
                        for (JsonNode item : value) {
                            sb.append("- ").append(item.asText()).append("\n");
                        }
                        sb.append("\n");
                    } else if (value.isObject()) {
                        sb.append("**").append(key).append(":** ").append(value.toPrettyString()).append("\n\n");
                    } else {
                        sb.append("**").append(key).append(":** ").append(value.asText()).append("\n\n");
                    }
                }
            }
            return sb.toString().trim();
        }

        // Case 2: { "features": [...] } or { "steps": [...] }
        for (String listKey : List.of("features", "steps", "items", "points", "list")) {
            if (jsonAnswer.has(listKey) && jsonAnswer.get(listKey).isArray()) {
                if (jsonAnswer.has("name") || jsonAnswer.has("title")) {
                    String title = jsonAnswer.has("name") ? jsonAnswer.get("name").asText() : jsonAnswer.get("title").asText();
                    sb.append("**").append(title).append(":**\n\n");
                }
                for (JsonNode item : jsonAnswer.get(listKey)) {
                    sb.append("- ").append(item.asText()).append("\n");
                }
                return sb.toString().trim();
            }
        }

        // Case 3: { "answer": "..." } or { "response": "..." }
        for (String textKey : List.of("answer", "response", "text", "content", "message")) {
            if (jsonAnswer.has(textKey)) {
                return jsonAnswer.get(textKey).asText();
            }
        }

        // Case 4: Simple key-value pairs - format as definition list
        if (jsonAnswer.isObject() && jsonAnswer.size() > 0 && jsonAnswer.size() <= 10) {
            var fields = jsonAnswer.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String key = formatTitle(field.getKey());
                JsonNode value = field.getValue();

                if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                    sb.append("**").append(key).append(":** ").append(value.asText()).append("\n");
                } else if (value.isArray() && value.size() > 0) {
                    sb.append("**").append(key).append(":**\n");
                    for (JsonNode item : value) {
                        sb.append("  - ").append(item.asText()).append("\n");
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString().trim();
            }
        }

        // Fallback: return pretty-printed JSON
        return jsonAnswer.toPrettyString();
    }

    private String formatTitle(String text) {
        if (text == null || text.isEmpty()) return text;
        // Convert camelCase or snake_case to Title Case
        String result = text.replaceAll("([a-z])([A-Z])", "$1 $2")
                           .replaceAll("_", " ");
        // Capitalize first letter of each word
        String[] words = result.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Apply locked parameter values from category overrides.
     * If a parameter is locked in the category, its value is forced regardless of user input.
     */
    private void applyLockedParameters(String categoryId, String toolName, Map<String, Object> parameters) {
        try {
            JsonNode mergedTool = toolRegistryClient.getMergedToolByName(categoryId, toolName);
            if (mergedTool == null) {
                return;
            }

            JsonNode params = mergedTool.get("parameters");
            if (params == null || !params.isArray()) {
                return;
            }

            for (JsonNode param : params) {
                if (param.has("locked") && param.get("locked").asBoolean()) {
                    String paramName = param.get("name").asText();
                    String lockedValue = param.has("lockedValue") ? param.get("lockedValue").asText() : null;

                    if (lockedValue != null && !lockedValue.isEmpty()) {
                        Object previousValue = parameters.get(paramName);
                        parameters.put(paramName, lockedValue);
                        log.info("Applied locked parameter: {}={} (was: {}) for tool {} in category {}",
                                paramName, lockedValue, previousValue, toolName, categoryId);
                    }
                }

                // Also check nested parameters
                applyLockedNestedParameters(param, parameters);
            }
        } catch (Exception e) {
            log.warn("Could not apply locked parameters for tool {} in category {}: {}",
                    toolName, categoryId, e.getMessage());
        }
    }

    /**
     * Recursively apply locked values for nested parameters.
     */
    private void applyLockedNestedParameters(JsonNode param, Map<String, Object> parameters) {
        JsonNode nestedParams = param.get("nestedParameters");
        if (nestedParams == null || !nestedParams.isArray()) {
            return;
        }

        for (JsonNode nested : nestedParams) {
            if (nested.has("locked") && nested.get("locked").asBoolean()) {
                String paramName = nested.get("name").asText();
                String lockedValue = nested.has("lockedValue") ? nested.get("lockedValue").asText() : null;

                if (lockedValue != null && !lockedValue.isEmpty()) {
                    parameters.put(paramName, lockedValue);
                    log.info("Applied locked nested parameter: {}={}", paramName, lockedValue);
                }
            }

            // Recurse for deeper nesting
            applyLockedNestedParameters(nested, parameters);
        }
    }
}
