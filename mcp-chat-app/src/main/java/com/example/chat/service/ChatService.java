package com.example.chat.service;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service to handle chat conversations and determine when to use RAG tools
 */
@Service
@Slf4j
public class ChatService {

    private final McpClientService mcpClient;
    private final LlmToolSelectionService toolSelector;
    private final CategoryAdminClient categoryAdminClient;
    private final com.example.chat.config.ToolSelectionConfig.ToolSelectionThresholds thresholds;
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    // Pattern to detect document ingestion intent
    private static final Pattern INGEST_PATTERN = Pattern.compile(
            "(?i)(ingest|add|upload|store|save)\\s+(this\\s+)?(document|text|content|information)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to detect tool commands: /tool_name arg1 arg2
    private static final Pattern TOOL_COMMAND_PATTERN = Pattern.compile(
            "^/([a-zA-Z_-]+)\\s*(.*)",
            Pattern.DOTALL
    );

    public ChatService(McpClientService mcpClient,
                       LlmToolSelectionService toolSelector,
                       CategoryAdminClient categoryAdminClient,
                       com.example.chat.config.ToolSelectionConfig.ToolSelectionThresholds thresholds) {
        this.mcpClient = mcpClient;
        this.toolSelector = toolSelector;
        this.categoryAdminClient = categoryAdminClient;
        this.thresholds = thresholds;
    }

    /**
     * Process user message and return response
     */
    public ChatMessage processMessage(String sessionId, String userMessage, String categoryId) {
        ChatSession session = getOrCreateSession(sessionId);

        // Store categoryId in session for filtering tools
        session.setCategoryId(categoryId);

        // Add user message to history
        ChatMessage userMsg = ChatMessage.user(userMessage);
        session.addMessage(userMsg);

        // Check for pending execution (confirmation or parameter completion)
        ChatMessage response;
        if (session.getPendingExecution() != null && !session.getPendingExecution().isExpired()) {
            response = handlePendingExecution(session, userMessage);
        } else {
            // Clear expired pending execution
            if (session.getPendingExecution() != null && session.getPendingExecution().isExpired()) {
                session.clearPendingExecution();
            }
            // Determine intent and execute appropriate action
            response = handleUserIntent(session, userMessage);
        }

        // Add assistant response to history
        session.addMessage(response);

        return response;
    }

    /**
     * Get chat session by ID
     */
    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Get or create a new session
     */
    public ChatSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new ChatSession(id));
    }

    /**
     * Create a new session
     */
    public ChatSession createSession() {
        String sessionId = UUID.randomUUID().toString();
        ChatSession session = new ChatSession(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * Get all active sessions
     */
    public Collection<ChatSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * Handle user intent - determine if it's ingestion or query
     */
    private ChatMessage handleUserIntent(ChatSession session, String userMessage) {
        // Priority 1: Help command
        if (userMessage.trim().equalsIgnoreCase("/help") || userMessage.trim().equalsIgnoreCase("/tools")) {
            return handleHelpCommand();
        }

        // Priority 2: Explicit slash commands (fast path - no LLM needed)
        Matcher toolMatcher = TOOL_COMMAND_PATTERN.matcher(userMessage.trim());
        if (toolMatcher.matches()) {
            String toolName = toolMatcher.group(1);
            String args = toolMatcher.group(2).trim();
            return handleToolCommand(session, toolName, args);
        }

        // Priority 3: LLM-powered tool selection (NEW)
        try {
            return handleLlmToolSelection(session, userMessage);
        } catch (Exception e) {
            log.error("LLM tool selection failed, falling back to pattern matching", e);
            // Fallback to original pattern-based logic
            return handleUserIntentWithPatterns(session, userMessage);
        }
    }

    /**
     * Fallback pattern-based intent detection (used if LLM fails)
     */
    private ChatMessage handleUserIntentWithPatterns(ChatSession session, String userMessage) {
        // Check if user wants to ingest a document
        Matcher ingestMatcher = INGEST_PATTERN.matcher(userMessage);
        if (ingestMatcher.find()) {
            return handleDocumentIngestion(session, userMessage);
        }

        // Check if message contains substantial text (possible document)
        if (userMessage.length() > 500 && userMessage.contains("\n")) {
            return handleDocumentIngestion(session, userMessage);
        }

        // Otherwise, treat as a question/query
        return handleQuery(session, userMessage);
    }

    /**
     * Handle explicit tool commands
     */
    private ChatMessage handleToolCommand(ChatSession session, String toolName, String args) {
        try {
            log.info("Executing tool command: {} with args: {}", toolName, args);

            // Parse arguments based on tool
            Map<String, Object> arguments = parseToolArguments(toolName, args);

            if (arguments == null) {
                return ChatMessage.assistant(
                        "❌ Invalid arguments for tool: " + toolName + "\n\n" +
                        "Use `/help` to see available tools and their usage.",
                        null
                );
            }

            // Execute the tool
            McpClientService.ToolExecutionResult result = mcpClient.executeTool(toolName, arguments);

            if (result.success()) {
                return formatToolResponse(toolName, result);
            } else {
                return ChatMessage.assistant(
                        "❌ Tool execution failed: " + result.errorMessage(),
                        Map.of("tool", toolName, "error", true)
                );
            }
        } catch (Exception e) {
            log.error("Error executing tool command", e);
            return ChatMessage.assistant(
                    "❌ Error: " + e.getMessage(),
                    Map.of("error", true)
            );
        }
    }

    /**
     * Parse tool arguments from command string
     */
    private Map<String, Object> parseToolArguments(String toolName, String args) {
        Map<String, Object> arguments = new HashMap<>();

        switch (toolName.toLowerCase()) {
            case "echo":
                if (args.isEmpty()) return null;
                arguments.put("message", args);
                break;

            case "add":
                String[] numbers = args.split("\\s+");
                if (numbers.length < 2) return null;
                try {
                    arguments.put("a", Double.parseDouble(numbers[0]));
                    arguments.put("b", Double.parseDouble(numbers[1]));
                } catch (NumberFormatException e) {
                    return null;
                }
                break;

            case "get_current_time":
                // No arguments needed
                break;

            case "jsonplaceholder-user":
                if (args.isEmpty()) return null;
                try {
                    arguments.put("userId", Integer.parseInt(args.trim()));
                } catch (NumberFormatException e) {
                    return null;
                }
                break;

            case "rag_ingest":
                // Parse: docId text
                int spaceIndex = args.indexOf(' ');
                if (spaceIndex == -1) return null;
                arguments.put("docId", args.substring(0, spaceIndex).trim());
                arguments.put("text", args.substring(spaceIndex + 1).trim());
                break;

            case "rag_query":
                if (args.isEmpty()) return null;
                // Check for topK parameter: question [topK]
                String[] parts = args.split("\\s+topK=");
                arguments.put("question", parts[0].trim());
                if (parts.length > 1) {
                    try {
                        arguments.put("topK", Integer.parseInt(parts[1].trim()));
                    } catch (NumberFormatException e) {
                        arguments.put("topK", 5);
                    }
                } else {
                    arguments.put("topK", 5);
                }
                break;

            case "get_application_by_id":
                if (args.isEmpty()) return null;
                arguments.put("applicationId", args.trim());
                break;

            case "get_application_services_with_dependencies":
                if (args.isEmpty()) return null;
                arguments.put("applicationId", args.trim());
                break;

            default:
                // For unknown tools, try to parse as generic key=value pairs
                if (!args.isEmpty()) {
                    // Simple parsing: assume single parameter or key=value format
                    if (args.contains("=")) {
                        String[] kvPairs = args.split("\\s+");
                        for (String kvPair : kvPairs) {
                            String[] kv = kvPair.split("=", 2);
                            if (kv.length == 2) {
                                arguments.put(kv[0].trim(), kv[1].trim());
                            }
                        }
                    }
                }
                break;
        }

        return arguments;
    }

    /**
     * Format tool execution response
     */
    private ChatMessage formatToolResponse(String toolName, McpClientService.ToolExecutionResult result) {
        StringBuilder response = new StringBuilder();
        response.append("✅ **Tool executed:** `").append(toolName).append("`\n\n");

        try {
            JsonNode contentNode = result.result().path("content").get(0).path("text");
            String responseText = contentNode.asText();

            // Try to parse as JSON for pretty printing
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                JsonNode parsed = mapper.readTree(responseText);

                // Special handling for different tools
                if (toolName.equals("echo")) {
                    response.append("**Echo:** ").append(parsed.path("echo").asText());
                } else if (toolName.equals("add")) {
                    response.append("**Result:** ").append(parsed.path("result").asDouble());
                } else if (toolName.equals("get_current_time")) {
                    response.append("**Time:** ").append(parsed.path("time").asText());
                } else if (toolName.equals("jsonplaceholder-user")) {
                    JsonNode data = parsed.path("data");
                    response.append("**User Information:**\n\n");
                    response.append("- **ID:** ").append(data.path("id").asInt()).append("\n");
                    response.append("- **Name:** ").append(data.path("name").asText()).append("\n");
                    response.append("- **Email:** ").append(data.path("email").asText()).append("\n");
                    response.append("- **Phone:** ").append(data.path("phone").asText()).append("\n");
                    response.append("- **Website:** ").append(data.path("website").asText()).append("\n");

                    JsonNode company = data.path("company");
                    if (!company.isMissingNode()) {
                        response.append("- **Company:** ").append(company.path("name").asText()).append("\n");
                    }
                } else if (toolName.equals("rag_query")) {
                    // Format RAG query response in natural language
                    // Handle both direct response and MCP-wrapped response
                    JsonNode data = parsed.has("data") ? parsed.path("data") : parsed;

                    if (data.path("success").asBoolean()) {
                        String answer = data.path("answer").asText();
                        JsonNode sources = data.path("sources");

                        response.append(answer).append("\n\n");

                        // Add sources if available
                        if (sources.isArray() && sources.size() > 0) {
                            response.append("**Sources:**\n");
                            for (JsonNode source : sources) {
                                response.append("- ").append(source.path("docId").asText());
                                response.append(" (chunk ").append(source.path("chunkIndex").asInt()).append(")");
                                response.append(" - relevance: ").append(String.format("%.2f", source.path("relevanceScore").asDouble()));
                                response.append("\n");
                            }
                        }
                    } else {
                        // Error case
                        response.append("❌ Query failed: ").append(data.path("errorMessage").asText());
                    }
                } else if (toolName.equals("rag_ingest")) {
                    // Format RAG ingest response in natural language
                    // Handle both direct response and MCP-wrapped response
                    JsonNode data = parsed.has("data") ? parsed.path("data") : parsed;

                    if (data.path("success").asBoolean()) {
                        String docId = data.path("docId").asText();
                        int chunkCount = data.path("chunksCreated").asInt();

                        response.append("✅ **Document ingested successfully!**\n\n");
                        response.append("- **Document ID:** ").append(docId).append("\n");
                        response.append("- **Chunks created:** ").append(chunkCount).append("\n");
                        response.append("\nYou can now query this document using natural language questions.");
                    } else {
                        // Error case
                        response.append("❌ Ingestion failed: ").append(data.path("message").asText());
                    }
                } else if (toolName.equals("get_application_services_with_dependencies")) {
                    // Format service dependency response
                    JsonNode data = parsed.has("data") ? parsed.path("data") : parsed;
                    if (data.isArray()) {
                        response.append("**Services:**\n\n");
                        for (JsonNode service : data) {
                            response.append("### ").append(service.path("name").asText())
                                   .append(" (`").append(service.path("serviceId").asText()).append("`)\n");
                            response.append("- **Description:** ").append(service.path("description").asText()).append("\n");
                            response.append("- **Endpoint:** ").append(service.path("endpoint").asText()).append("\n");

                            JsonNode operations = service.path("operations");
                            if (operations.isArray() && operations.size() > 0) {
                                response.append("- **Operations:**\n");
                                for (JsonNode op : operations) {
                                    response.append("  - `").append(op.path("httpMethod").asText()).append(" ")
                                           .append(op.path("path").asText()).append("` - ")
                                           .append(op.path("description").asText()).append("\n");

                                    JsonNode deps = op.path("dependencies");
                                    if (deps.isArray() && deps.size() > 0) {
                                        response.append("    Dependencies:\n");
                                        for (JsonNode dep : deps) {
                                            response.append("      - ")
                                                   .append(dep.path("dependentApplicationId").asText()).append("/")
                                                   .append(dep.path("dependentServiceId").asText()).append("/")
                                                   .append(dep.path("dependentOperationId").asText())
                                                   .append(" (").append(dep.path("dependencyType").asText()).append(")\n");
                                        }
                                    }
                                }
                            }
                            response.append("\n");
                        }
                    } else {
                        response.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
                    }
                } else if (toolName.equals("get_application_by_id")) {
                    // Format application info response
                    JsonNode data = parsed.has("data") ? parsed.path("data") : parsed;
                    response.append("**Application:** ").append(data.path("name").asText()).append("\n\n");
                    response.append("- **Application ID:** ").append(data.path("applicationId").asText()).append("\n");
                    response.append("- **Description:** ").append(data.path("description").asText()).append("\n");
                    response.append("- **Owner:** ").append(data.path("owner").asText()).append("\n");
                    response.append("- **Status:** ").append(data.path("status").asText()).append("\n\n");

                    JsonNode services = data.path("services");
                    if (services.isArray() && services.size() > 0) {
                        response.append("**Services:** (").append(services.size()).append(" total)\n");
                        for (JsonNode service : services) {
                            response.append("- ").append(service.path("name").asText())
                                   .append(" (`").append(service.path("serviceId").asText()).append("`)\n");
                        }
                    }
                } else {
                    // Generic JSON display
                    response.append("```json\n");
                    response.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
                    response.append("\n```");
                }
            } catch (Exception e) {
                // Not JSON or parsing failed, show raw text
                response.append(responseText);
            }
        } catch (Exception e) {
            response.append("Response: ").append(result.result().toString());
        }

        return ChatMessage.assistant(response.toString(), Map.of("tool", toolName));
    }

    /**
     * Handle LLM-powered tool selection.
     * The LLM analyzes the user message and selects the appropriate tool
     * based on tool descriptions and parameter schemas from MCP.
     */
    private ChatMessage handleLlmToolSelection(ChatSession session, String userMessage) {
        // 1. Get available tools from MCP - the LLM will use their descriptions
        List<McpClientService.Tool> availableTools = mcpClient.listTools();

        // 1.5. Filter by category if categoryId is set (dynamic tool selection)
        if (session.getCategoryId() != null && !session.getCategoryId().isBlank()) {
            List<String> enabledToolIds = categoryAdminClient.getEnabledTools(session.getCategoryId());
            availableTools = availableTools.stream()
                .filter(tool -> enabledToolIds.contains(tool.name()))
                .collect(Collectors.toList());
            log.info("Filtered to {} enabled tools for category {}",
                     availableTools.size(), session.getCategoryId());
        }

        // 2. Pattern-based fast path: Check for specific entity ID patterns
        // This helps when LLM struggles with "what services in APP-XXX" type queries
        String fastPathResult = tryFastPathPatternMatch(userMessage, availableTools);
        if (fastPathResult != null) {
            return ChatMessage.assistant(fastPathResult, Map.of("selectionMode", "pattern-match"));
        }

        // 3. Let LLM select tool and extract parameters based on tool documentation
        log.debug("Using LLM tool selection for message: {}", userMessage);
        LlmToolSelectionService.ToolSelectionResult selection = toolSelector.selectTool(userMessage, availableTools);

        log.info("LLM selected tool: {} with confidence: {}", selection.selectedTool(), selection.confidence());

        // 4. Handle based on confidence
        if (selection.confidence() < thresholds.lowThreshold()) {
            // Low confidence - ask user to choose
            return askUserToChooseTool(session, selection, userMessage);
        } else if (selection.confidence() < thresholds.highThreshold()) {
            // Medium confidence - show reasoning and ask confirmation
            return askUserToConfirm(session, selection);
        }

        // 5. High confidence - validate parameters
        McpClientService.Tool selectedTool = findTool(availableTools, selection.selectedTool());
        if (selectedTool == null) {
            return ChatMessage.assistant(
                "I couldn't find the tool '" + selection.selectedTool() + "'. Use /help to see available tools.",
                null
            );
        }

        LlmToolSelectionService.ValidationResult validation = toolSelector.validateParameters(
            selection.extractedParameters(),
            selectedTool.inputSchema()
        );

        if (!validation.isValid()) {
            // Missing or invalid parameters - ask user
            return askForMissingParameters(session, selection, validation, selectedTool);
        }

        // 6. Execute tool
        return executeToolWithLlmSelection(selection, userMessage);
    }

    /**
     * Fast path pattern matching for specific entity queries.
     * Detects patterns like "APP-XXX" and routes to appropriate tools.
     * Returns formatted response if matched, null otherwise.
     */
    private String tryFastPathPatternMatch(String userMessage, List<McpClientService.Tool> availableTools) {
        // Pattern 1: Detect APP-XXX pattern (application IDs)
        java.util.regex.Pattern appPattern = java.util.regex.Pattern.compile("\\bAPP-[A-Z0-9-]+\\b");
        java.util.regex.Matcher appMatcher = appPattern.matcher(userMessage.toUpperCase());

        if (appMatcher.find()) {
            String applicationId = appMatcher.group();

            // CRITICAL: Only use pattern matching if the question is clearly about the SPECIFIC application
            // Check for question patterns that indicate querying about a specific application:
            // - "what services are in APP-XXX"
            // - "show me services for APP-XXX"
            // - "APP-XXX services"
            // - "get APP-XXX application"
            // - "what is APP-XXX"

            String lowerMessage = userMessage.toLowerCase();

            // Check if this is a specific application query (not a general knowledge question)
            // Look for patterns like "in APP-XXX", "for APP-XXX", "APP-XXX application", "about APP-XXX"
            boolean isSpecificAppQuery = lowerMessage.matches(".*\\b(in|for|of|about)\\s+" + applicationId.toLowerCase().replace("-", "\\-") + "\\b.*")
                || lowerMessage.matches(".*\\b" + applicationId.toLowerCase().replace("-", "\\-") + "\\s+(application|services|info|details|operations)\\b.*")
                || lowerMessage.matches(".*\\b(what|show|get|list).*\\b" + applicationId.toLowerCase().replace("-", "\\-") + "\\b.*");

            if (!isSpecificAppQuery) {
                // This is NOT a specific application query - it's a general question that happens to mention an app ID
                // Let LLM handle it instead
                log.debug("Pattern matched APP-XXX but query doesn't seem to be about that specific application - using LLM");
                return null;
            }

            // Check if query is about services/operations
            if (lowerMessage.matches(".*\\b(service|operation|dependency|dependencies).*")) {
                // Try to use get_application_services_with_dependencies
                McpClientService.Tool servicesTool = findTool(availableTools, "get_application_services_with_dependencies");
                if (servicesTool != null) {
                    log.info("Fast path: Detected application services query for {}", applicationId);

                    Map<String, Object> params = Map.of("applicationId", applicationId);
                    McpClientService.ToolExecutionResult result = mcpClient.executeTool("get_application_services_with_dependencies", params);

                    if (result.success()) {
                        return formatApplicationServicesResponse(applicationId, result);
                    }
                }
            } else {
                // General application info query
                McpClientService.Tool appTool = findTool(availableTools, "get_application_by_id");
                if (appTool != null) {
                    log.info("Fast path: Detected application info query for {}", applicationId);

                    Map<String, Object> params = Map.of("applicationId", applicationId);
                    McpClientService.ToolExecutionResult result = mcpClient.executeTool("get_application_by_id", params);

                    if (result.success()) {
                        return formatApplicationInfoResponse(applicationId, result);
                    }
                }
            }
        }

        // Pattern 2: Detect user ID pattern (for jsonplaceholder-user)
        if (userMessage.toLowerCase().matches(".*\\b(user|show me user|get user).*\\b\\d+\\b.*")) {
            java.util.regex.Pattern userIdPattern = java.util.regex.Pattern.compile("\\b(\\d+)\\b");
            java.util.regex.Matcher userIdMatcher = userIdPattern.matcher(userMessage);

            if (userIdMatcher.find()) {
                String userId = userIdMatcher.group(1);
                McpClientService.Tool userTool = findTool(availableTools, "jsonplaceholder-user");

                if (userTool != null) {
                    log.info("Fast path: Detected user query for ID {}", userId);

                    Map<String, Object> params = Map.of("userId", Integer.parseInt(userId));
                    McpClientService.ToolExecutionResult result = mcpClient.executeTool("jsonplaceholder-user", params);

                    if (result.success()) {
                        return "✅ **Tool executed:** `jsonplaceholder-user` (pattern-matched)\n\n" +
                               formatToolResponse("jsonplaceholder-user", result);
                    }
                }
            }
        }

        return null; // No pattern match, proceed with LLM selection
    }

    /**
     * Format application services response
     */
    private String formatApplicationServicesResponse(String applicationId, McpClientService.ToolExecutionResult result) {
        try {
            StringBuilder response = new StringBuilder();
            response.append("✅ **Tool executed:** `get_application_services_with_dependencies` (pattern-matched)\n\n");
            response.append("**Services for ").append(applicationId).append(":**\n\n");

            // Parse MCP response structure: content[0].text contains JSON string
            JsonNode contentNode = result.result().path("content").get(0).path("text");
            String responseText = contentNode.asText();

            // Parse the JSON string to get actual data
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode parsed = mapper.readTree(responseText);

            // Extract data array
            JsonNode data = parsed.has("data") ? parsed.path("data") : parsed;

            if (data.isArray()) {
                int serviceCount = 0;
                for (JsonNode service : data) {
                    serviceCount++;
                    response.append(serviceCount).append(". **")
                            .append(service.path("name").asText())
                            .append("** (`").append(service.path("serviceId").asText()).append("`)\n");
                    response.append("   - **Description:** ").append(service.path("description").asText()).append("\n");
                    response.append("   - **Endpoint:** ").append(service.path("endpoint").asText()).append("\n");

                    JsonNode operations = service.path("operations");
                    if (operations.isArray() && operations.size() > 0) {
                        response.append("   - **Operations:** ").append(operations.size()).append(" total\n");
                        for (JsonNode op : operations) {
                            response.append("     - `").append(op.path("httpMethod").asText())
                                   .append(" ").append(op.path("path").asText())
                                   .append("` - ").append(op.path("description").asText()).append("\n");

                            JsonNode deps = op.path("dependencies");
                            if (deps.isArray() && deps.size() > 0) {
                                response.append("       **Dependencies:** ");
                                for (int i = 0; i < deps.size(); i++) {
                                    if (i > 0) response.append(", ");
                                    JsonNode dep = deps.get(i);
                                    response.append(dep.path("dependentApplicationId").asText())
                                           .append("/").append(dep.path("dependentServiceId").asText())
                                           .append(" (").append(dep.path("dependencyType").asText()).append(")");
                                }
                                response.append("\n");
                            }
                        }
                    }
                    response.append("\n");
                }
            }

            return response.toString();
        } catch (Exception e) {
            log.error("Error formatting application services response", e);
            return "✅ **Tool executed:** `get_application_services_with_dependencies`\n\n" +
                   formatToolResponse("get_application_services_with_dependencies", result).getContent();
        }
    }

    /**
     * Format application info response
     */
    private String formatApplicationInfoResponse(String applicationId, McpClientService.ToolExecutionResult result) {
        try {
            StringBuilder response = new StringBuilder();
            response.append("✅ **Tool executed:** `get_application_by_id` (pattern-matched)\n\n");

            // Parse MCP response structure: content[0].text contains JSON string
            JsonNode contentNode = result.result().path("content").get(0).path("text");
            String responseText = contentNode.asText();

            // Parse the JSON string to get actual data
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode parsed = mapper.readTree(responseText);

            // Extract data
            JsonNode data = parsed.has("data") ? parsed.path("data") : parsed;

            response.append("**Application Information:**\n\n");
            response.append("- **Name:** ").append(data.path("name").asText()).append("\n");
            response.append("- **Application ID:** ").append(data.path("applicationId").asText()).append("\n");
            response.append("- **Description:** ").append(data.path("description").asText()).append("\n");
            response.append("- **Owner:** ").append(data.path("owner").asText()).append("\n");
            response.append("- **Status:** ").append(data.path("status").asText()).append("\n\n");

            JsonNode services = data.path("services");
            if (services.isArray() && services.size() > 0) {
                response.append("**Services:** (").append(services.size()).append(" total)\n");
                for (JsonNode service : services) {
                    response.append("- ").append(service.path("name").asText())
                           .append(" (`").append(service.path("serviceId").asText()).append("`)\n");
                }
            }

            return response.toString();
        } catch (Exception e) {
            log.error("Error formatting application info response", e);
            return "✅ **Tool executed:** `get_application_by_id`\n\n" +
                   formatToolResponse("get_application_by_id", result).getContent();
        }
    }

    /**
     * Find tool by name
     */
    private McpClientService.Tool findTool(List<McpClientService.Tool> tools, String toolName) {
        return tools.stream()
            .filter(t -> t.name().equals(toolName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Ask user to choose from alternatives (low confidence)
     */
    private ChatMessage askUserToChooseTool(ChatSession session,
                                           LlmToolSelectionService.ToolSelectionResult selection,
                                           String originalMessage) {
        StringBuilder message = new StringBuilder();
        message.append("I'm not sure which tool you want to use. Did you mean:\n\n");

        // Show top alternatives
        List<LlmToolSelectionService.AlternativeTool> alternatives = selection.alternatives();
        if (alternatives.isEmpty()) {
            message.append("I couldn't determine the appropriate tool for your request.\n\n");
        } else {
            int index = 1;
            for (LlmToolSelectionService.AlternativeTool alt : alternatives.stream().limit(thresholds.maxAlternatives()).toList()) {
                message.append(String.format("%d. **%s** - %s\n", index++, alt.toolName(), alt.reasoning()));
            }
            message.append("\n");
        }

        message.append("Please clarify which tool you'd like to use, or use explicit syntax like `/add 5 10`");

        return ChatMessage.assistant(message.toString(), Map.of(
            "type", "clarification",
            "confidence", selection.confidence(),
            "reasoning", selection.reasoning() != null ? selection.reasoning() : ""
        ));
    }

    /**
     * Ask user to confirm tool selection (medium confidence)
     */
    private ChatMessage askUserToConfirm(ChatSession session, LlmToolSelectionService.ToolSelectionResult selection) {
        String message = String.format(
            "I think you want to use **%s**\n\n" +
            "Reasoning: %s\n\n" +
            "Confidence: %.0f%%\n\n" +
            "Should I proceed? (yes/no or provide the correct command)",
            selection.selectedTool(),
            selection.reasoning(),
            selection.confidence() * 100
        );

        // Set pending execution
        session.setPendingExecution(
            selection.selectedTool(),
            selection.extractedParameters(),
            "awaiting_confirmation",
            thresholds.parameterTimeoutMinutes()
        );

        return ChatMessage.assistant(message, Map.of(
            "type", "confirmation",
            "tool", selection.selectedTool(),
            "confidence", selection.confidence()
        ));
    }

    /**
     * Ask for missing parameters
     */
    private ChatMessage askForMissingParameters(ChatSession session,
                                               LlmToolSelectionService.ToolSelectionResult selection,
                                               LlmToolSelectionService.ValidationResult validation,
                                               McpClientService.Tool tool) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("I'll use **%s** but I need some more information:\n\n", selection.selectedTool()));

        if (!validation.missingRequired().isEmpty()) {
            message.append("Missing required parameters:\n");
            for (String param : validation.missingRequired()) {
                // Get parameter description from schema
                String description = getParameterDescription(tool.inputSchema(), param);
                message.append(String.format("- **%s**: %s\n", param, description));
            }
        }

        if (!validation.typeErrors().isEmpty()) {
            message.append("\nInvalid parameters:\n");
            for (String error : validation.typeErrors()) {
                message.append(String.format("- %s\n", error));
            }
        }

        message.append("\nPlease provide the missing information.");

        // Set pending execution
        session.setPendingExecution(
            selection.selectedTool(),
            selection.extractedParameters(),
            "awaiting_parameters",
            thresholds.parameterTimeoutMinutes()
        );

        return ChatMessage.assistant(message.toString(), Map.of(
            "type", "parameter_request",
            "tool", selection.selectedTool()
        ));
    }

    /**
     * Get parameter description from schema
     */
    private String getParameterDescription(JsonNode schema, String paramName) {
        if (schema != null && schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            if (properties.has(paramName)) {
                JsonNode paramSchema = properties.get(paramName);
                if (paramSchema.has("description")) {
                    return paramSchema.get("description").asText();
                }
            }
        }
        return "Please provide this parameter";
    }

    /**
     * Execute tool with LLM selection
     */
    private ChatMessage executeToolWithLlmSelection(LlmToolSelectionService.ToolSelectionResult selection, String userQuestion) {
        // Execute the tool
        McpClientService.ToolExecutionResult result = mcpClient.executeTool(
            selection.selectedTool(),
            selection.extractedParameters()
        );

        if (!result.success()) {
            return ChatMessage.assistant(
                "❌ Tool execution failed: " + result.errorMessage(),
                Map.of("tool", selection.selectedTool(), "error", true)
            );
        }

        // Format response
        ChatMessage toolResponse = formatToolResponse(selection.selectedTool(), result);

        // Skip extra LLM call for RAG queries - they already return well-formed answers
        // This saves ~7 seconds per query
        String finalAnswer;
        if ("rag_query".equals(selection.selectedTool())) {
            // RAG already returns a natural language answer, use it directly
            finalAnswer = toolResponse.getContent();
        } else {
            // For other tools, generate natural language answer
            finalAnswer = generateNaturalAnswer(userQuestion, toolResponse.getContent(), selection.selectedTool());
        }

        Map<String, Object> metadata = new HashMap<>(toolResponse.getMetadata());
        metadata.put("llmConfidence", selection.confidence());
        metadata.put("llmReasoning", selection.reasoning());
        metadata.put("selectionMode", "llm");

        return ChatMessage.assistant(finalAnswer, metadata);
    }

    /**
     * Generate a natural language answer to the user's question based on the tool result
     */
    private String generateNaturalAnswer(String userQuestion, String toolResult, String toolName) {
        try {
            String prompt = buildAnswerGenerationPrompt(userQuestion, toolResult, toolName);

            // Use the LLM to generate a natural answer
            String answer = toolSelector.generateAnswer(prompt);

            // Clean up the answer
            answer = answer.trim();

            // If answer is too short or looks like an error, fall back to formatted result
            if (answer.length() < 10 || answer.toLowerCase().contains("cannot") || answer.toLowerCase().contains("unable")) {
                return toolResult;
            }

            return answer;
        } catch (Exception e) {
            log.error("Failed to generate natural answer, using formatted result", e);
            return toolResult;
        }
    }

    /**
     * Build prompt for generating natural language answer
     */
    private String buildAnswerGenerationPrompt(String userQuestion, String toolResult, String toolName) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful assistant. The user asked: \"").append(userQuestion).append("\"\n\n");
        prompt.append("A tool was executed and returned this information:\n\n");
        prompt.append(toolResult).append("\n\n");
        prompt.append("Based on this information, provide a direct, natural language answer to the user's question.\n");
        prompt.append("CRITICAL REQUIREMENTS:\n");
        prompt.append("1. If the data contains a LIST or TABLE, you MUST include ALL items exactly as shown\n");
        prompt.append("2. Do NOT summarize, skip, or paraphrase list items\n");
        prompt.append("3. If there are service names, IDs, or structured data - copy them EXACTLY\n");
        prompt.append("4. Answer ONLY what the user asked for, but include complete data\n");
        prompt.append("5. Be concise but NEVER omit data items\n");
        prompt.append("6. For structured data (services, operations, dependencies), preserve ALL details\n\n");
        prompt.append("Example: If the tool returned 3 services, your answer MUST mention all 3 services with their exact names and IDs.\n\n");
        prompt.append("Answer:");

        return prompt.toString();
    }

    /**
     * Handle pending tool execution (confirmation or parameter completion)
     */
    private ChatMessage handlePendingExecution(ChatSession session, String userMessage) {
        ChatSession.PendingToolExecution pending = session.getPendingExecution();

        if ("awaiting_confirmation".equals(pending.getState())) {
            // User is confirming tool selection
            if (userMessage.toLowerCase().matches(".*(yes|ok|sure|proceed|go ahead).*")) {
                session.clearPendingExecution();
                // Execute the pending tool
                McpClientService.ToolExecutionResult result = mcpClient.executeTool(
                    pending.getTool(),
                    pending.getParameters()
                );
                return formatToolResponse(pending.getTool(), result);
            } else if (userMessage.toLowerCase().matches(".*(no|cancel|stop).*")) {
                session.clearPendingExecution();
                return ChatMessage.assistant("Okay, cancelled. How can I help you?", null);
            } else {
                // User might be providing a different command
                session.clearPendingExecution();
                return handleUserIntent(session, userMessage);
            }
        } else if ("awaiting_parameters".equals(pending.getState())) {
            // User is providing missing parameters
            List<McpClientService.Tool> tools = mcpClient.listTools();
            McpClientService.Tool tool = findTool(tools, pending.getTool());

            if (tool == null) {
                session.clearPendingExecution();
                return ChatMessage.assistant("Tool not found. Please try again.", null);
            }

            // Try to extract parameters with LLM
            Map<String, Object> newParams = toolSelector.extractParameters(userMessage, tool);

            // Merge with existing parameters
            Map<String, Object> merged = new HashMap<>(pending.getParameters());
            merged.putAll(newParams);

            // Validate again
            LlmToolSelectionService.ValidationResult validation = toolSelector.validateParameters(
                merged, tool.inputSchema());

            if (validation.isValid()) {
                session.clearPendingExecution();
                // Execute with complete parameters
                McpClientService.ToolExecutionResult result = mcpClient.executeTool(pending.getTool(), merged);
                return formatToolResponse(pending.getTool(), result);
            } else {
                // Still missing - ask again
                return askForMissingParameters(session,
                    new LlmToolSelectionService.ToolSelectionResult(pending.getTool(), 1.0, merged, "", List.of()),
                    validation, tool);
            }
        }

        // Shouldn't reach here
        session.clearPendingExecution();
        return handleUserIntent(session, userMessage);
    }

    /**
     * Handle help command
     */
    private ChatMessage handleHelpCommand() {
        StringBuilder help = new StringBuilder();
        help.append("# 🛠️ Available MCP Tools\n\n");
        help.append("Use `/tool_name args` to execute any tool:\n\n");

        help.append("## Built-in Tools\n\n");

        help.append("**`/echo <message>`**\n");
        help.append("Echoes back your message\n");
        help.append("*Example:* `/echo Hello World`\n\n");

        help.append("**`/add <number1> <number2>`**\n");
        help.append("Adds two numbers together\n");
        help.append("*Example:* `/add 5 10`\n\n");

        help.append("**`/get_current_time`**\n");
        help.append("Returns the current server time\n");
        help.append("*Example:* `/get_current_time`\n\n");

        help.append("## API Tools\n\n");

        help.append("**`/jsonplaceholder-user <userId>`**\n");
        help.append("Fetches user info from JSONPlaceholder API (IDs 1-10)\n");
        help.append("*Example:* `/jsonplaceholder-user 1`\n\n");

        help.append("## RAG Tools\n\n");

        help.append("**`/rag_ingest <docId> <text>`**\n");
        help.append("Ingests a document into the knowledge base\n");
        help.append("*Example:* `/rag_ingest my-doc This is the document content...`\n\n");

        help.append("**`/rag_query <question> [topK=N]`**\n");
        help.append("Queries the knowledge base\n");
        help.append("*Example:* `/rag_query What is machine learning? topK=3`\n\n");

        help.append("---\n\n");
        help.append("💡 **Tip:** You can also just type naturally:\n");
        help.append("- Paste long text to auto-ingest documents\n");
        help.append("- Ask questions to auto-query RAG\n\n");

        help.append("Type `/tools` to see this help again.");

        return ChatMessage.assistant(help.toString(), Map.of("command", "help"));
    }

    /**
     * Handle document ingestion
     */
    private ChatMessage handleDocumentIngestion(ChatSession session, String userMessage) {
        try {
            // Extract or generate document ID
            String docId = "doc_" + System.currentTimeMillis();

            // Extract document text (remove ingestion command if present)
            String docText = userMessage;
            Matcher matcher = INGEST_PATTERN.matcher(userMessage);
            if (matcher.find()) {
                docText = userMessage.substring(matcher.end()).trim();
                // Remove common delimiters
                docText = docText.replaceFirst("^[:\\-]\\s*", "");
            }

            if (docText.length() < 50) {
                return ChatMessage.assistant(
                        "The document text seems too short. Please provide more content to ingest. " +
                        "You can paste document content directly, or use a command like: " +
                        "'Ingest this document: [your text here]'",
                        null
                );
            }

            log.info("Ingesting document {} with {} characters", docId, docText.length());

            // Call MCP to ingest
            McpClientService.ToolExecutionResult result = mcpClient.ingestDocument(docId, docText);

            if (result.success()) {
                // Parse the result
                JsonNode data = result.result().path("content").get(0).path("text");
                String responseText = data.asText();

                // Try to parse the inner JSON
                try {
                    JsonNode innerData = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseText);
                    int chunks = innerData.path("data").path("chunksCreated").asInt();

                    String message = String.format(
                            "✅ Document ingested successfully!\n\n" +
                            "**Document ID:** %s\n" +
                            "**Chunks created:** %d\n" +
                            "**Characters:** %,d\n\n" +
                            "You can now ask questions about this document!",
                            docId, chunks, docText.length()
                    );

                    return ChatMessage.assistant(message, Map.of(
                            "tool", "rag_ingest",
                            "docId", docId,
                            "chunks", chunks
                    ));
                } catch (Exception e) {
                    return ChatMessage.assistant(
                            "✅ Document ingested! You can now ask questions about it.",
                            Map.of("tool", "rag_ingest", "docId", docId)
                    );
                }
            } else {
                return ChatMessage.assistant(
                        "❌ Failed to ingest document: " + result.errorMessage(),
                        null
                );
            }
        } catch (Exception e) {
            log.error("Error ingesting document", e);
            return ChatMessage.assistant(
                    "❌ Error ingesting document: " + e.getMessage(),
                    null
            );
        }
    }

    /**
     * Handle query/question
     */
    private ChatMessage handleQuery(ChatSession session, String userMessage) {
        try {
            log.info("Querying RAG with question: {}", userMessage);

            // Call MCP to query RAG
            McpClientService.ToolExecutionResult result = mcpClient.queryRag(userMessage, 5);

            if (result.success()) {
                // Parse the result
                JsonNode contentNode = result.result().path("content").get(0).path("text");
                String responseText = contentNode.asText();

                // Try to parse the inner JSON
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    JsonNode innerData = mapper.readTree(responseText);
                    JsonNode data = innerData.path("data");

                    String answer = data.path("answer").asText();
                    JsonNode sourcesNode = data.path("sources");

                    // Build response with sources
                    StringBuilder response = new StringBuilder();
                    response.append(answer);

                    if (sourcesNode.isArray() && sourcesNode.size() > 0) {
                        response.append("\n\n**Sources:**\n");
                        for (int i = 0; i < sourcesNode.size() && i < 3; i++) {
                            JsonNode source = sourcesNode.get(i);
                            String docId = source.path("docId").asText();
                            double score = source.path("relevanceScore").asDouble();
                            String text = source.path("text").asText();

                            response.append(String.format(
                                    "\n%d. *%s* (relevance: %.2f)\n",
                                    i + 1, docId, score
                            ));

                            // Add snippet
                            if (text.length() > 150) {
                                response.append("   > ").append(text.substring(0, 150)).append("...\n");
                            } else {
                                response.append("   > ").append(text).append("\n");
                            }
                        }
                    }

                    return ChatMessage.assistant(response.toString(), Map.of(
                            "tool", "rag_query",
                            "sources", sourcesNode.size()
                    ));

                } catch (Exception e) {
                    // Fallback if parsing fails
                    return ChatMessage.assistant(responseText, Map.of("tool", "rag_query"));
                }
            } else {
                return ChatMessage.assistant(
                        "❌ Failed to query: " + result.errorMessage(),
                        null
                );
            }
        } catch (Exception e) {
            log.error("Error querying RAG", e);
            return ChatMessage.assistant(
                    "❌ Error processing query: " + e.getMessage(),
                    null
            );
        }
    }
}
