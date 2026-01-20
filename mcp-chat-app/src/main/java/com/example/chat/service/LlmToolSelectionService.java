package com.example.chat.service;

import com.example.chat.llm.ChatClient;
import com.example.chat.service.McpClientService.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM-powered tool selection and parameter extraction service
 */
@Service
public class LlmToolSelectionService {
    private static final Logger logger = LoggerFactory.getLogger(LlmToolSelectionService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmToolSelectionService(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        logger.info("LlmToolSelectionService initialized with ChatClient: {}", chatClient.getClass().getName());
    }

    /**
     * Get the ChatClient class name (for debugging)
     */
    public String getChatClientClassName() {
        return chatClient.getClass().getName();
    }

    /**
     * Analyze user message and select appropriate tool using LLM
     */
    public ToolSelectionResult selectTool(String userMessage, List<Tool> availableTools) {
        try {
            logger.debug("selectTool called with {} available tools", availableTools.size());

            // 1. Build prompt with tool descriptions
            String prompt = buildToolSelectionPrompt(userMessage, availableTools);
            logger.debug("Built prompt, length={}", prompt.length());

            // 2. Call LLM with low temperature for consistency
            logger.debug("Calling LLM...");
            String llmResponse = chatClient.chatOnce(prompt, 0.2, 512);
            logger.debug("LLM raw response (first 500 chars): {}",
                llmResponse != null ? llmResponse.substring(0, Math.min(500, llmResponse.length())) : "NULL");

            // 3. Parse JSON response
            ToolSelectionResult result = parseToolSelection(llmResponse);
            logger.info("Tool selection result: tool={}, confidence={}, reasoning={}",
                result.selectedTool(), result.confidence(), result.reasoning());
            return result;

        } catch (Exception e) {
            logger.error("LLM tool selection failed: {} - {}", e.getClass().getName(), e.getMessage(), e);
            // Return low confidence result to trigger fallback
            return new ToolSelectionResult(
                null,
                0.0,
                Map.of(),
                "LLM selection failed: " + e.getClass().getName() + ": " + e.getMessage(),
                List.of()
            );
        }
    }

    /**
     * Extract parameters from natural language for a specific tool
     */
    public Map<String, Object> extractParameters(String userMessage, Tool tool) {
        try {
            String prompt = buildParameterExtractionPrompt(userMessage, tool);
            String llmResponse = chatClient.chatOnce(prompt, 0.2, 256);

            JsonNode responseNode = objectMapper.readTree(llmResponse);
            JsonNode parametersNode = responseNode.get("parameters");

            if (parametersNode != null && parametersNode.isObject()) {
                return objectMapper.convertValue(parametersNode, Map.class);
            }

            return Map.of();

        } catch (Exception e) {
            logger.error("Parameter extraction failed", e);
            return Map.of();
        }
    }

    /**
     * Validate extracted parameters against tool schema
     */
    public ValidationResult validateParameters(Map<String, Object> params, JsonNode schema) {
        List<String> missingRequired = new ArrayList<>();
        List<String> typeErrors = new ArrayList<>();

        if (schema == null || !schema.has("properties")) {
            return new ValidationResult(List.of(), List.of());
        }

        JsonNode properties = schema.get("properties");
        JsonNode required = schema.get("required");

        // Check for missing required parameters
        if (required != null && required.isArray()) {
            required.forEach(requiredField -> {
                String fieldName = requiredField.asText();
                if (!params.containsKey(fieldName) || params.get(fieldName) == null) {
                    missingRequired.add(fieldName);
                }
            });
        }

        // Check for type errors
        params.forEach((key, value) -> {
            if (properties.has(key)) {
                JsonNode propertySchema = properties.get(key);
                String expectedType = propertySchema.has("type") ? propertySchema.get("type").asText() : "any";

                if (!isValidType(value, expectedType)) {
                    typeErrors.add(String.format("Parameter '%s' should be %s but got %s",
                        key, expectedType, value.getClass().getSimpleName()));
                }
            }
        });

        return new ValidationResult(missingRequired, typeErrors);
    }

    private boolean isValidType(Object value, String expectedType) {
        if (value == null) return false;

        return switch (expectedType.toLowerCase()) {
            case "string" -> value instanceof String;
            case "integer", "int" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> true; // Unknown type, assume valid
        };
    }

    private String buildToolSelectionPrompt(String userMessage, List<Tool> tools) {
        StringBuilder prompt = new StringBuilder();

        // System instruction
        prompt.append("You are an intelligent tool selection assistant. Your task is to analyze the user's message and select the most appropriate tool from the available tools list.\n\n");

        // User message first for context
        prompt.append("## User Message\n");
        prompt.append("\"").append(userMessage).append("\"\n\n");

        // Available tools with rich documentation from schema
        prompt.append("## Available Tools\n\n");

        for (Tool tool : tools) {
            prompt.append("### ").append(tool.name()).append("\n");

            // Description
            if (tool.description() != null && !tool.description().isEmpty()) {
                prompt.append("**Description:** ").append(tool.description()).append("\n");
            }

            // Parameters with full documentation from schema
            if (tool.inputSchema() != null && tool.inputSchema().has("properties")) {
                JsonNode properties = tool.inputSchema().get("properties");
                JsonNode required = tool.inputSchema().get("required");

                List<String> requiredFields = new ArrayList<>();
                if (required != null && required.isArray()) {
                    required.forEach(field -> requiredFields.add(field.asText()));
                }

                prompt.append("**Parameters:**\n");
                properties.fields().forEachRemaining(entry -> {
                    String paramName = entry.getKey();
                    JsonNode paramSchema = entry.getValue();

                    String type = paramSchema.has("type") ? paramSchema.get("type").asText() : "any";
                    String description = paramSchema.has("description") ? paramSchema.get("description").asText() : "";
                    String example = paramSchema.has("example") ? paramSchema.get("example").asText() : "";
                    String defaultValue = paramSchema.has("default") ? paramSchema.get("default").asText() : "";
                    boolean isRequired = requiredFields.contains(paramName);

                    prompt.append("  - `").append(paramName).append("` (").append(type).append(")");
                    if (isRequired) {
                        prompt.append(" **[REQUIRED]**");
                    } else if (!defaultValue.isEmpty()) {
                        prompt.append(" [default: ").append(defaultValue).append("]");
                    }
                    if (!description.isEmpty()) {
                        prompt.append(": ").append(description);
                    }
                    if (!example.isEmpty()) {
                        prompt.append(" Example: ").append(example);
                    }
                    prompt.append("\n");
                });
            } else {
                prompt.append("**Parameters:** None\n");
            }
            prompt.append("\n");
        }

        // Instructions for selection
        prompt.append("## Instructions\n\n");
        prompt.append("**CRITICAL FIRST STEP: Identify the question type**\n\n");

        prompt.append("### Question Type 1: GENERAL KNOWLEDGE / HOW-TO QUESTIONS\n");
        prompt.append("These are questions about concepts, best practices, explanations, tutorials, or how to do something.\n");
        prompt.append("**Key indicators:**\n");
        prompt.append("- Questions starting with: \"how do\", \"how to\", \"what is\", \"explain\", \"what are the best practices\"\n");
        prompt.append("- Questions about general concepts WITHOUT mentioning specific entity IDs in the USER'S question\n");
        prompt.append("- Questions about architecture, patterns, methodologies, technologies\n");
        prompt.append("**Examples:**\n");
        prompt.append("- \"how do you develop microservice using spring boot\" → rag_query\n");
        prompt.append("- \"what is REST API\" → rag_query\n");
        prompt.append("- \"explain batch processing\" → rag_query\n");
        prompt.append("- \"best practices for microservices\" → rag_query\n");
        prompt.append("- \"how to implement authentication\" → rag_query\n");
        prompt.append("**Action:** Use `rag_query` tool for knowledge base search\n\n");

        prompt.append("### Question Type 2: SPECIFIC DATA LOOKUP\n");
        prompt.append("These are questions requesting specific data about a particular entity (application, service, user) that exists in a database.\n");
        prompt.append("**Key indicators:**\n");
        prompt.append("- User's message contains SPECIFIC ENTITY IDs: APP-XXX, SVC-XXX, user ID numbers\n");
        prompt.append("- Questions asking ABOUT a specific entity (\"in APP-ORDER-PROC\", \"for APP-USER-MGMT\", \"user 5\")\n");
        prompt.append("- Questions like: \"what services are IN [specific app]\", \"show me [specific entity]\", \"get [specific ID]\"\n");
        prompt.append("**Examples:**\n");
        prompt.append("- \"what services are in APP-ORDER-PROC application\" → get_application_services_with_dependencies (APP-ORDER-PROC is in USER'S question)\n");
        prompt.append("- \"get services for APP-USER-MGMT\" → get_application_services_with_dependencies (APP-USER-MGMT is in USER'S question)\n");
        prompt.append("- \"show me user 5\" → jsonplaceholder-user (user ID 5 is in USER'S question)\n");
        prompt.append("- \"what is APP-PAYMENT-GW\" → get_application_by_id (APP-PAYMENT-GW is in USER'S question)\n");
        prompt.append("**Action:** Use specific data lookup tools (get_application_by_id, get_application_services_with_dependencies, jsonplaceholder-user)\n\n");

        prompt.append("**CRITICAL DISTINCTION:**\n");
        prompt.append("- If the USER'S MESSAGE is asking HOW TO do something general → rag_query\n");
        prompt.append("- If the USER'S MESSAGE mentions a SPECIFIC ENTITY ID and asks about THAT entity → data lookup tool\n");
        prompt.append("- IGNORE entity IDs that appear in tool descriptions/examples - ONLY look at the USER'S MESSAGE above!\n\n");

        prompt.append("### Other Instructions:\n");
        prompt.append("1. Select the SINGLE best tool from the available tools list\n");
        prompt.append("2. Extract parameter values from the USER'S MESSAGE (not from tool descriptions)\n");
        prompt.append("3. Use ONLY tool names that exist in the available tools list\n");
        prompt.append("4. Set confidence based on clarity:\n");
        prompt.append("   - 0.9-1.0: Very clear intent and tool match\n");
        prompt.append("   - 0.7-0.9: Clear intent, good tool match\n");
        prompt.append("   - 0.5-0.7: Somewhat clear, possible match\n");
        prompt.append("   - Below 0.5: Unclear intent or poor match\n\n");

        // Response format
        prompt.append("## Response Format\n");
        prompt.append("Respond with valid JSON only (no markdown, no explanation):\n");
        prompt.append("```\n");
        prompt.append("{\n");
        prompt.append("  \"tool\": \"exact_tool_name\",\n");
        prompt.append("  \"confidence\": 0.95,\n");
        prompt.append("  \"parameters\": {\n");
        prompt.append("    \"param1\": \"extracted_value\",\n");
        prompt.append("    \"param2\": 123\n");
        prompt.append("  },\n");
        prompt.append("  \"reasoning\": \"Brief explanation of why this tool was selected\"\n");
        prompt.append("}\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    private String buildParameterExtractionPrompt(String userMessage, Tool tool) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a parameter extraction assistant. Extract parameters from the user message for the selected tool.\n\n");
        prompt.append("Tool: ").append(tool.name()).append("\n");
        prompt.append("Description: ").append(tool.description()).append("\n\n");

        if (tool.inputSchema() != null && tool.inputSchema().has("properties")) {
            JsonNode properties = tool.inputSchema().get("properties");
            JsonNode required = tool.inputSchema().get("required");

            List<String> requiredFields = new ArrayList<>();
            if (required != null && required.isArray()) {
                required.forEach(field -> requiredFields.add(field.asText()));
            }

            prompt.append("Required Parameters:\n");
            properties.fields().forEachRemaining(entry -> {
                String paramName = entry.getKey();
                if (requiredFields.contains(paramName)) {
                    JsonNode paramSchema = entry.getValue();
                    String type = paramSchema.has("type") ? paramSchema.get("type").asText() : "any";
                    String description = paramSchema.has("description") ? paramSchema.get("description").asText() : "";
                    prompt.append(String.format("- %s (%s): %s\n", paramName, type, description));
                }
            });

            prompt.append("\nOptional Parameters:\n");
            properties.fields().forEachRemaining(entry -> {
                String paramName = entry.getKey();
                if (!requiredFields.contains(paramName)) {
                    JsonNode paramSchema = entry.getValue();
                    String type = paramSchema.has("type") ? paramSchema.get("type").asText() : "any";
                    String description = paramSchema.has("description") ? paramSchema.get("description").asText() : "";
                    prompt.append(String.format("- %s (%s): %s\n", paramName, type, description));
                }
            });
        }

        prompt.append("\nUser Message: \"").append(userMessage).append("\"\n\n");
        prompt.append("Extract the parameters and respond with JSON ONLY:\n");
        prompt.append("{\n");
        prompt.append("  \"parameters\": {\n");
        prompt.append("    \"param1\": \"extracted_value1\",\n");
        prompt.append("    \"param2\": 123\n");
        prompt.append("  },\n");
        prompt.append("  \"missing\": [\"list\", \"of\", \"missing\", \"required\", \"params\"],\n");
        prompt.append("  \"confidence\": 0.9\n");
        prompt.append("}\n\n");
        prompt.append("Rules:\n");
        prompt.append("- Only extract parameters that are clearly stated\n");
        prompt.append("- Use correct types (string, number, boolean, integer)\n");
        prompt.append("- Mark required parameters as missing if not found\n");
        prompt.append("- Confidence 0.0 to 1.0 based on clarity of extraction\n");

        return prompt.toString();
    }

    private ToolSelectionResult parseToolSelection(String llmResponse) throws JsonProcessingException {
        logger.debug("parseToolSelection input (first 500 chars): {}",
            llmResponse != null ? llmResponse.substring(0, Math.min(500, llmResponse.length())) : "NULL");

        if (llmResponse == null || llmResponse.isBlank()) {
            logger.warn("LLM returned null or blank response");
            return new ToolSelectionResult(null, 0.0, Map.of(), "LLM returned empty response", List.of());
        }

        // Clean up response if it contains markdown formatting
        String cleanedResponse = llmResponse.trim();

        // Remove markdown headers (## Response, # Response, etc.)
        cleanedResponse = cleanedResponse.replaceAll("(?m)^#+\\s+.*$", "").trim();

        // Remove markdown code blocks
        if (cleanedResponse.startsWith("```json")) {
            cleanedResponse = cleanedResponse.substring(7);
        }
        if (cleanedResponse.startsWith("```")) {
            cleanedResponse = cleanedResponse.substring(3);
        }
        if (cleanedResponse.endsWith("```")) {
            cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
        }
        cleanedResponse = cleanedResponse.trim();

        // Find the first { and last } to extract just the JSON object
        int firstBrace = cleanedResponse.indexOf('{');
        int lastBrace = cleanedResponse.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleanedResponse = cleanedResponse.substring(firstBrace, lastBrace + 1);
        }
        cleanedResponse = cleanedResponse.trim();

        logger.debug("parseToolSelection cleanedResponse: {}", cleanedResponse);

        JsonNode responseNode = objectMapper.readTree(cleanedResponse);

        // Handle both expected format and llama.cpp function-call format
        // Expected: {"tool": "name", "confidence": 0.9, "parameters": {...}, "reasoning": "..."}
        // llama.cpp may return: {"name": "name", "parameters": {...}}
        String tool = null;
        if (responseNode.has("tool")) {
            tool = responseNode.get("tool").asText();
        } else if (responseNode.has("name")) {
            // Fallback for llama.cpp function-call format
            tool = responseNode.get("name").asText();
            logger.debug("Using 'name' field as tool (llama.cpp function-call format)");
        }

        double confidence = responseNode.has("confidence") ? responseNode.get("confidence").asDouble() : 0.8; // Default to 0.8 if not specified
        String reasoning = responseNode.has("reasoning") ? responseNode.get("reasoning").asText() : "Tool selected by LLM";

        // Debug: Log what was parsed
        logger.info("Parsed from LLM: tool={}, confidence={}, reasoning={}, hasToolField={}, hasConfidenceField={}",
            tool, confidence, reasoning, responseNode.has("tool"), responseNode.has("confidence"));

        Map<String, Object> parameters = new HashMap<>();
        if (responseNode.has("parameters") && responseNode.get("parameters").isObject()) {
            JsonNode paramsNode = responseNode.get("parameters");

            // Convert parameters with proper type handling
            paramsNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                // Auto-convert to appropriate Java types
                if (value.isNumber()) {
                    if (value.isIntegralNumber()) {
                        parameters.put(key, value.asLong());
                    } else {
                        parameters.put(key, value.asDouble());
                    }
                } else if (value.isBoolean()) {
                    parameters.put(key, value.asBoolean());
                } else if (value.isTextual()) {
                    String textValue = value.asText();
                    // Try to auto-convert string numbers to actual numbers
                    try {
                        if (textValue.contains(".")) {
                            parameters.put(key, Double.parseDouble(textValue));
                        } else {
                            // Try as long first, fallback to string
                            try {
                                parameters.put(key, Long.parseLong(textValue));
                            } catch (NumberFormatException e2) {
                                parameters.put(key, textValue); // Keep as string
                            }
                        }
                    } catch (NumberFormatException e) {
                        parameters.put(key, textValue); // Not a number, keep as string
                    }
                } else if (value.isArray() || value.isObject()) {
                    parameters.put(key, objectMapper.convertValue(value, Object.class));
                } else {
                    parameters.put(key, value.asText());
                }
            });
        }

        List<AlternativeTool> alternatives = new ArrayList<>();
        if (responseNode.has("alternatives") && responseNode.get("alternatives").isArray()) {
            responseNode.get("alternatives").forEach(altNode -> {
                String altTool = altNode.has("tool") ? altNode.get("tool").asText() : "";
                double altConfidence = altNode.has("confidence") ? altNode.get("confidence").asDouble() : 0.0;
                String altReasoning = altNode.has("reasoning") ? altNode.get("reasoning").asText() : "";
                alternatives.add(new AlternativeTool(altTool, altConfidence, altReasoning));
            });
        }

        return new ToolSelectionResult(tool, confidence, parameters, reasoning, alternatives);
    }

    // Records
    public record ToolSelectionResult(
        String selectedTool,
        double confidence,
        Map<String, Object> extractedParameters,
        String reasoning,
        List<AlternativeTool> alternatives
    ) {}

    public record AlternativeTool(
        String toolName,
        double confidence,
        String reasoning
    ) {}

    public record ValidationResult(
        List<String> missingRequired,
        List<String> typeErrors
    ) {
        public boolean isValid() {
            return missingRequired.isEmpty() && typeErrors.isEmpty();
        }
    }

    /**
     * Generate a natural language answer using the LLM
     */
    public String generateAnswer(String prompt) {
        try {
            // Use low temperature (0.1) for deterministic, consistent responses
            // Especially important for structured data like lists and tables
            return chatClient.chatOnce(prompt, 0.1, 512);
        } catch (Exception e) {
            logger.error("Failed to generate answer with LLM", e);
            throw new RuntimeException("Failed to generate natural language answer", e);
        }
    }
}
