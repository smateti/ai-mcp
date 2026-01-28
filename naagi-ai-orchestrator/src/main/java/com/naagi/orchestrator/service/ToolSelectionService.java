package com.naagi.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.orchestrator.llm.LlmClient;
import com.naagi.orchestrator.model.AlternativeTool;
import com.naagi.orchestrator.model.ToolSelectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ToolSelectionService {

    private final LlmClient llmClient;
    private final ToolRegistryClient toolRegistryClient;
    private final ObjectMapper objectMapper;

    @Value("${naagi.tool-selection.confidence.high-threshold:0.8}")
    private double highConfidenceThreshold;

    @Value("${naagi.tool-selection.confidence.low-threshold:0.5}")
    private double lowConfidenceThreshold;

    public ToolSelectionService(LlmClient llmClient, ToolRegistryClient toolRegistryClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.toolRegistryClient = toolRegistryClient;
        this.objectMapper = objectMapper;
    }

    public ToolSelectionResult selectTool(String userMessage, List<JsonNode> availableTools) {
        String prompt = buildToolSelectionPrompt(userMessage, availableTools);

        log.debug("Tool selection prompt: {}", prompt);

        String llmResponse = llmClient.chat(prompt, 0.2, 512);

        log.debug("LLM response: {}", llmResponse);

        return parseToolSelection(llmResponse);
    }

    private String buildToolSelectionPrompt(String userMessage, List<JsonNode> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an intelligent tool selection assistant. Analyze the user's message and select the most appropriate tool.\n\n");

        // CRITICAL: Question type identification
        sb.append("## CRITICAL FIRST STEP: Identify the question type\n\n");

        sb.append("### Type 1: GENERAL KNOWLEDGE / HOW-TO / CONCEPTUAL QUESTIONS\n");
        sb.append("These are questions about concepts, explanations, best practices, features, capabilities, or how to do something.\n");
        sb.append("**Key indicators:**\n");
        sb.append("- Questions starting with: \"how do\", \"how to\", \"what is\", \"what are\", \"explain\", \"how many\", \"why\"\n");
        sb.append("- Questions about general concepts, features, capabilities, best practices\n");
        sb.append("- Questions about technology, architecture, patterns, methodologies\n");
        sb.append("- Questions that can be answered from documentation or knowledge base\n");
        sb.append("**Examples:**\n");
        sb.append("- \"How many features does Spring Boot have?\" → rag_query\n");
        sb.append("- \"What is REST API?\" → rag_query\n");
        sb.append("- \"How do you develop microservices?\" → rag_query\n");
        sb.append("- \"Explain batch processing\" → rag_query\n");
        sb.append("- \"Best practices for Spring Boot\" → rag_query\n");
        sb.append("**ACTION:** Use `rag_query` tool to search the knowledge base\n\n");

        sb.append("### Type 2: SPECIFIC ACTION / DATA LOOKUP / TOOL EXECUTION\n");
        sb.append("These are requests to PERFORM an action or look up SPECIFIC entity data.\n");
        sb.append("**Key indicators:**\n");
        sb.append("- Requests containing SPECIFIC IDs, names, or entities (APP-XXX, user 5, etc.)\n");
        sb.append("- Requests to CREATE, GENERATE, FETCH, or EXECUTE something specific\n");
        sb.append("- Commands like: \"create a project\", \"generate code\", \"get data for X\"\n");
        sb.append("**ACTION:** Select a tool from the Available Tools list below that matches the action\n\n");
        sb.append("### CRITICAL: If no tool in the Available Tools list matches the user's request, set tool to null and explain in reasoning.\n\n");

        sb.append("## Available Tools\n\n");

        int index = 1;
        for (JsonNode tool : tools) {
            String name = tool.has("toolId") ? tool.get("toolId").asText() : tool.get("name").asText();
            String description = tool.has("humanReadableDescription") && !tool.get("humanReadableDescription").isNull()
                    ? tool.get("humanReadableDescription").asText()
                    : (tool.has("description") ? tool.get("description").asText() : "No description");

            sb.append(index++).append(". **").append(name).append("** - ").append(description).append("\n");

            // Add parameter info
            if (tool.has("parameters") && tool.get("parameters").isArray()) {
                sb.append("   Parameters: ");
                List<String> params = new ArrayList<>();
                for (JsonNode param : tool.get("parameters")) {
                    String paramName = param.get("name").asText();
                    String paramType = param.has("type") ? param.get("type").asText() : "string";
                    boolean required = param.has("required") && param.get("required").asBoolean();
                    params.add(paramName + " (" + paramType + (required ? ", required" : "") + ")");
                }
                sb.append(String.join(", ", params)).append("\n");
            }
        }

        sb.append("\n## User Message\n\"").append(userMessage).append("\"\n\n");

        sb.append("## Response Format\n");
        sb.append("Respond with JSON ONLY (no markdown, no explanation):\n");
        sb.append("{\n");
        sb.append("  \"tool\": \"tool_name or null if no tool matches\",\n");
        sb.append("  \"confidence\": 0.95,\n");
        sb.append("  \"parameters\": {\n");
        sb.append("    \"param1\": \"value1\"\n");
        sb.append("  },\n");
        sb.append("  \"reasoning\": \"Brief explanation of why this tool was selected\"\n");
        sb.append("}\n\n");

        sb.append("## Rules\n");
        sb.append("- CRITICAL: You can ONLY select tools from the 'Available Tools' list above. Never invent tool names!\n");
        sb.append("- If no tool matches the user's request, set \"tool\": null and explain why in reasoning\n");
        sb.append("- confidence must be 0.0 to 1.0\n");
        sb.append("- CRITICAL: Use the EXACT parameter names as listed above for each tool. Do NOT rename parameters!\n");
        sb.append("- For rag_query tool: the parameter is named 'question' (NOT 'query'). Example: {\"question\": \"How many features does Spring Boot have?\"}\n");
        sb.append("- Only include parameters you can confidently extract from the message\n");
        sb.append("- For KNOWLEDGE questions (what is, how to, explain, how many, why, best practices) → USE rag_query\n");
        sb.append("- If unsure, set confidence low\n");

        return sb.toString();
    }

    private ToolSelectionResult parseToolSelection(String llmResponse) {
        try {
            // Clean up response if it's wrapped in markdown
            String cleaned = llmResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            // Fix invalid JSON escape sequences that LLMs sometimes produce
            // \' is not valid JSON (single quotes don't need escaping), replace with just '
            cleaned = cleaned.replace("\\'", "'");
            // Also handle other common invalid escapes
            cleaned = cleaned.replace("\\`", "`");

            JsonNode root = objectMapper.readTree(cleaned);

            // Handle both "tool" and "name" fields (LLM may use either)
            String tool = null;
            if (root.has("tool") && !root.get("tool").isNull()) {
                tool = root.get("tool").asText();
            } else if (root.has("name") && !root.get("name").isNull()) {
                // Fallback for LLM returning "name" instead of "tool"
                tool = root.get("name").asText();
                log.debug("LLM returned 'name' field instead of 'tool': {}", tool);
            }

            // Default to 0.8 confidence if tool was selected but confidence not specified
            double confidence = root.has("confidence") ? root.get("confidence").asDouble() : (tool != null ? 0.8 : 0.0);
            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : (tool != null ? "Tool selected by LLM" : "");

            Map<String, Object> parameters = new HashMap<>();
            if (root.has("parameters") && root.get("parameters").isObject()) {
                parameters = objectMapper.convertValue(root.get("parameters"), new TypeReference<Map<String, Object>>() {});
            }

            List<AlternativeTool> alternatives = new ArrayList<>();
            if (root.has("alternatives") && root.get("alternatives").isArray()) {
                for (JsonNode alt : root.get("alternatives")) {
                    alternatives.add(new AlternativeTool(
                            alt.get("tool").asText(),
                            alt.has("confidence") ? alt.get("confidence").asDouble() : 0.0,
                            alt.has("reasoning") ? alt.get("reasoning").asText() : ""
                    ));
                }
            }

            return new ToolSelectionResult(tool, confidence, parameters, reasoning, alternatives);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", llmResponse, e);
            return new ToolSelectionResult(null, 0.0, new HashMap<>(), "Failed to parse LLM response", List.of());
        }
    }

    public boolean isHighConfidence(double confidence) {
        return confidence >= highConfidenceThreshold;
    }

    public boolean isLowConfidence(double confidence) {
        return confidence < lowConfidenceThreshold;
    }
}
