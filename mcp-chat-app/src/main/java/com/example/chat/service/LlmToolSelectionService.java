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
    }

    /**
     * Analyze user message and select appropriate tool using LLM
     */
    public ToolSelectionResult selectTool(String userMessage, List<Tool> availableTools) {
        try {
            // 1. Build prompt with tool descriptions
            String prompt = buildToolSelectionPrompt(userMessage, availableTools);

            // 2. Call LLM with low temperature for consistency
            String llmResponse = chatClient.chatOnce(prompt, 0.2, 512);

            // 3. Parse JSON response
            return parseToolSelection(llmResponse);

        } catch (Exception e) {
            logger.error("LLM tool selection failed", e);
            // Return low confidence result to trigger fallback
            return new ToolSelectionResult(
                null,
                0.0,
                Map.of(),
                "LLM selection failed: " + e.getMessage(),
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
        prompt.append("You are a precise tool selection assistant. Analyze the user's message and select the most appropriate tool.\n\n");
        prompt.append("CRITICAL: Extract parameters with EXACT types as specified below.\n\n");
        prompt.append("Available Tools:\n");

        int index = 1;
        for (Tool tool : tools) {
            prompt.append(String.format("%d. %s - %s\n", index++, tool.name(), tool.description()));

            // Extract parameters from schema
            if (tool.inputSchema() != null && tool.inputSchema().has("properties")) {
                JsonNode properties = tool.inputSchema().get("properties");
                JsonNode required = tool.inputSchema().get("required");

                List<String> requiredFields = new ArrayList<>();
                if (required != null && required.isArray()) {
                    required.forEach(field -> requiredFields.add(field.asText()));
                }

                prompt.append("   Parameters:\n");
                properties.fields().forEachRemaining(entry -> {
                    String paramName = entry.getKey();
                    JsonNode paramSchema = entry.getValue();
                    String type = paramSchema.has("type") ? paramSchema.get("type").asText() : "any";
                    boolean isRequired = requiredFields.contains(paramName);

                    prompt.append(String.format("     - %s: %s%s\n",
                        paramName, type.toUpperCase(), isRequired ? " (REQUIRED)" : " (optional)"));
                });
            } else {
                prompt.append("   Parameters: none\n");
            }
            prompt.append("\n");
        }

        prompt.append("User Message: \"").append(userMessage).append("\"\n\n");
        prompt.append("RESPOND WITH VALID JSON ONLY (no markdown, no code blocks, no explanation):\n");
        prompt.append("{\n");
        prompt.append("  \"tool\": \"tool_name\",\n");
        prompt.append("  \"confidence\": 0.95,\n");
        prompt.append("  \"parameters\": {\n");
        prompt.append("    \"stringParam\": \"text value\",\n");
        prompt.append("    \"numberParam\": 42,\n");
        prompt.append("    \"integerParam\": 10\n");
        prompt.append("  },\n");
        prompt.append("  \"reasoning\": \"Brief explanation\",\n");
        prompt.append("  \"alternatives\": [{\"tool\": \"other_tool\", \"confidence\": 0.3, \"reasoning\": \"Could also be...\"}]\n");
        prompt.append("}\n\n");
        prompt.append("CRITICAL RULES:\n");
        prompt.append("1. For NUMBER type: use actual numbers like 42, 3.14 (NOT strings like \"42\")\n");
        prompt.append("2. For INTEGER type: use integers like 5, 10 (NOT strings like \"5\")\n");
        prompt.append("3. For STRING type: use quoted strings like \"hello\"\n");
        prompt.append("4. confidence must be between 0.0 and 1.0\n");
        prompt.append("5. Extract ALL recognizable parameters from the message\n");
        prompt.append("6. If numbers are mentioned (e.g., \"add 3 and 5\"), extract them as actual numbers: {\"a\": 3, \"b\": 5}\n");

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
        // Clean up response if it contains markdown code blocks
        String cleanedResponse = llmResponse.trim();
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

        JsonNode responseNode = objectMapper.readTree(cleanedResponse);

        String tool = responseNode.has("tool") ? responseNode.get("tool").asText() : null;
        double confidence = responseNode.has("confidence") ? responseNode.get("confidence").asDouble() : 0.0;
        String reasoning = responseNode.has("reasoning") ? responseNode.get("reasoning").asText() : "";

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
