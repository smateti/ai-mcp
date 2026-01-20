package com.naag.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naag.orchestrator.llm.LlmClient;
import com.naag.orchestrator.metrics.OrchestratorMetrics;
import com.naag.orchestrator.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class StreamingOrchestrationService {

    private final ToolSelectionService toolSelectionService;
    private final ToolRegistryClient toolRegistryClient;
    private final McpGatewayClient mcpGatewayClient;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final OrchestratorMetrics metrics;
    private final String ragServiceUrl;
    private final HttpClient httpClient;

    public StreamingOrchestrationService(
            ToolSelectionService toolSelectionService,
            ToolRegistryClient toolRegistryClient,
            McpGatewayClient mcpGatewayClient,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            OrchestratorMetrics metrics,
            @Value("${naag.services.rag-service.url}") String ragServiceUrl) {
        this.toolSelectionService = toolSelectionService;
        this.toolRegistryClient = toolRegistryClient;
        this.mcpGatewayClient = mcpGatewayClient;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.ragServiceUrl = ragServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Stream orchestrated response - handles both RAG queries and tool calls
     */
    public void orchestrateStream(OrchestrationRequest request, SseEmitter emitter) {
        long orchestrationStart = System.currentTimeMillis();
        log.info("Streaming orchestration for: {} (category: {})", request.getMessage(), request.getCategoryId());

        try {
            // Get available tools
            List<JsonNode> availableTools;
            if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
                availableTools = toolRegistryClient.getToolsByCategory(request.getCategoryId());
            } else {
                availableTools = toolRegistryClient.getAllTools();
            }

            if (availableTools.isEmpty()) {
                sendError(emitter, "No tools available for this category.");
                return;
            }

            // Select tool
            long selectionStart = System.currentTimeMillis();
            ToolSelectionResult selection = toolSelectionService.selectTool(request.getMessage(), availableTools);
            long selectionTime = System.currentTimeMillis() - selectionStart;
            metrics.recordToolSelectionTime(selectionTime);

            log.info("Tool selection: tool={}, confidence={}", selection.getSelectedTool(), selection.getConfidence());

            boolean isHigh = toolSelectionService.isHighConfidence(selection.getConfidence());
            boolean isLow = toolSelectionService.isLowConfidence(selection.getConfidence());
            metrics.recordConfidence(selection.getConfidence(), isHigh, isLow);

            // Send tool selection info
            sendToolInfo(emitter, selection);

            if (selection.getSelectedTool() == null || isLow) {
                // Low confidence - send clarification message
                String clarification = buildClarificationMessage(selection);
                sendTextTokens(emitter, clarification);
                sendDone(emitter);
            } else if (!isHigh) {
                // Medium confidence - ask for confirmation
                String confirmation = buildConfirmationMessage(selection);
                sendTextTokens(emitter, confirmation);
                sendDone(emitter);
            } else {
                // High confidence - execute and stream
                executeAndStream(request, selection, emitter);
            }

            long orchestrationTime = System.currentTimeMillis() - orchestrationStart;
            metrics.recordOrchestrationTime(orchestrationTime);
            log.info("[TIMING] Streaming orchestration: {}ms", orchestrationTime);

        } catch (Exception e) {
            log.error("Streaming orchestration error", e);
            metrics.recordOrchestrationError();
            sendError(emitter, "Error: " + e.getMessage());
        }
    }

    private void executeAndStream(OrchestrationRequest request, ToolSelectionResult selection, SseEmitter emitter) {
        String toolName = selection.getSelectedTool();
        Map<String, Object> parameters = new HashMap<>(selection.getExtractedParameters());

        // For RAG queries, use streaming endpoint
        if (toolName != null && toolName.startsWith("rag_query")) {
            if (request.getCategoryId() != null) {
                parameters.put("category", request.getCategoryId());
            }
            streamRagQuery(parameters, emitter);
        } else {
            // For other tools, execute and stream result
            executeToolAndStream(request.getMessage(), toolName, parameters, emitter);
        }
    }

    /**
     * Stream RAG query results directly from RAG service.
     * First checks for FAQ match to avoid unnecessary LLM calls.
     */
    private void streamRagQuery(Map<String, Object> parameters, SseEmitter emitter) {
        try {
            String question = (String) parameters.getOrDefault("question",
                    parameters.getOrDefault("query", ""));
            String category = (String) parameters.get("category");
            int topK = parameters.containsKey("topK") ?
                    ((Number) parameters.get("topK")).intValue() : 5;

            // Check for FAQ match first to avoid LLM call if similar question was already answered
            JsonNode faqMatch = checkFaqMatch(question, category);
            if (faqMatch != null && faqMatch.has("found") && faqMatch.get("found").asBoolean()) {
                String faqAnswer = faqMatch.get("answer").asText();
                double matchScore = faqMatch.has("score") ? faqMatch.get("score").asDouble() : 0.0;
                log.info("FAQ match found for question (score: {}), returning cached answer", String.format("%.2f", matchScore));

                // Send FAQ source info
                try {
                    ObjectNode sourceInfo = objectMapper.createObjectNode();
                    sourceInfo.put("type", "faq_cache");
                    sourceInfo.put("faqId", faqMatch.has("faqId") ? faqMatch.get("faqId").asText() : "");
                    sourceInfo.put("matchScore", matchScore);
                    emitter.send(SseEmitter.event().name("source").data(objectMapper.writeValueAsString(sourceInfo)));
                } catch (Exception e) {
                    log.debug("Could not send source info", e);
                }

                // Stream the FAQ answer directly
                sendTextTokens(emitter, faqAnswer);
                sendDone(emitter);
                return;
            }

            // No FAQ match - proceed with RAG query
            ObjectNode ragRequest = objectMapper.createObjectNode();
            ragRequest.put("question", question);
            ragRequest.put("topK", topK);
            if (category != null) {
                ragRequest.put("category", category);
            }

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(ragServiceUrl + "/api/rag/query/stream"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(ragRequest)))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(httpReq,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                sendError(emitter, "RAG service error: HTTP " + response.statusCode());
                return;
            }

            // Forward SSE events from RAG service
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        String eventName = line.substring(6).trim();
                        String dataLine = reader.readLine();
                        if (dataLine != null && dataLine.startsWith("data:")) {
                            String data = dataLine.substring(5);
                            if (!"token".equals(eventName)) {
                                data = data.trim();
                            }
                            emitter.send(SseEmitter.event().name(eventName).data(data));

                            if ("done".equals(eventName)) {
                                break;
                            }
                        }
                    } else if (line.startsWith("data:")) {
                        String data = line.substring(5);
                        if (!data.trim().isEmpty()) {
                            emitter.send(SseEmitter.event().name("token").data(data));
                        }
                    }
                }
            }
            emitter.complete();

        } catch (Exception e) {
            log.error("RAG streaming error", e);
            sendError(emitter, "RAG query failed: " + e.getMessage());
        }
    }

    /**
     * Execute a non-RAG tool and stream the response
     */
    private void executeToolAndStream(String userQuestion, String toolName,
                                       Map<String, Object> parameters, SseEmitter emitter) {
        try {
            long executionStart = System.currentTimeMillis();

            // Execute the tool via MCP gateway
            JsonNode toolResult = mcpGatewayClient.executeTool(toolName, parameters);

            long executionTime = System.currentTimeMillis() - executionStart;
            metrics.recordToolExecutionTime(executionTime);
            log.info("[TIMING] Tool execution ({}): {}ms", toolName, executionTime);

            if (toolResult == null) {
                sendError(emitter, "Tool execution failed - no result returned.");
                return;
            }

            // Extract and format the tool result
            String formattedResult = formatToolResult(userQuestion, toolName, toolResult);

            // Stream the result as tokens
            sendTextTokens(emitter, formattedResult);
            sendDone(emitter);

        } catch (Exception e) {
            log.error("Tool execution error", e);
            metrics.recordToolExecutionError();
            sendError(emitter, "Tool execution failed: " + e.getMessage());
        }
    }

    private String formatToolResult(String userQuestion, String toolName, JsonNode toolResult) {
        try {
            // Extract the actual data from MCP response wrapper
            String jsonData = extractToolData(toolResult);

            // Use LLM to generate natural language response
            String prompt = String.format("""
                TASK: Answer a question using provided data. Output PLAIN TEXT only.

                CRITICAL: Do NOT output JSON, function calls, or tool invocations.

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
            log.error("Error formatting tool result", e);
            return String.format("**Result from %s:**\n```json\n%s\n```",
                    toolName, toolResult.toPrettyString());
        }
    }

    private String extractToolData(JsonNode toolResult) {
        try {
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
        return toolResult.toPrettyString();
    }

    private String buildClarificationMessage(ToolSelectionResult selection) {
        StringBuilder sb = new StringBuilder();
        if (selection.getSelectedTool() == null) {
            sb.append("I don't have a tool available to handle this request.\n\n");
            if (selection.getReasoning() != null && !selection.getReasoning().isEmpty()) {
                sb.append("**Reason:** ").append(selection.getReasoning()).append("\n\n");
            }
            sb.append("This type of query may require a tool that hasn't been registered yet, ");
            sb.append("or you could try rephrasing your question to search the knowledge base.");
        } else {
            sb.append("I'm not sure which tool you want to use.");
            if (!selection.getAlternatives().isEmpty()) {
                sb.append(" Did you mean:\n\n");
                int index = 1;
                for (AlternativeTool alt : selection.getAlternatives()) {
                    sb.append(index++).append(". **").append(alt.getToolName())
                            .append("** - ").append(alt.getReasoning()).append("\n");
                }
            }
            sb.append("\nPlease clarify which tool you'd like to use.");
        }
        return sb.toString();
    }

    private String buildConfirmationMessage(ToolSelectionResult selection) {
        return String.format(
                "I think you want to use **%s**\n\n" +
                        "Reasoning: %s\n\n" +
                        "Confidence: %.0f%%\n\n" +
                        "Should I proceed? (yes/no)",
                selection.getSelectedTool(),
                selection.getReasoning(),
                selection.getConfidence() * 100
        );
    }

    private void sendToolInfo(SseEmitter emitter, ToolSelectionResult selection) {
        try {
            ObjectNode info = objectMapper.createObjectNode();
            info.put("tool", selection.getSelectedTool());
            info.put("confidence", selection.getConfidence());
            if (selection.getReasoning() != null) {
                info.put("reasoning", selection.getReasoning());
            }
            emitter.send(SseEmitter.event().name("tool").data(objectMapper.writeValueAsString(info)));
        } catch (Exception e) {
            log.warn("Failed to send tool info", e);
        }
    }

    private void sendTextTokens(SseEmitter emitter, String text) {
        try {
            // Send text in chunks to simulate streaming
            String[] words = text.split("(?<=\\s)");
            for (String word : words) {
                if (!word.isEmpty()) {
                    String escaped = escapeJson(word);
                    emitter.send(SseEmitter.event().name("token").data("{\"t\":\"" + escaped + "\"}"));
                    Thread.sleep(10); // Small delay for visual effect
                }
            }
        } catch (Exception e) {
            log.warn("Error sending text tokens", e);
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (Exception e) {
            log.warn("Failed to send done event", e);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (Exception e) {
            log.warn("Failed to send error", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                // Ignore
            }
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Check if the question matches an existing FAQ.
     * Uses the RAG service's FAQ matching endpoint which respects the faqQueryEnabled setting.
     * Returns null if no match found or if FAQ query is disabled.
     */
    private JsonNode checkFaqMatch(String question, String categoryId) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("question", question);
            if (categoryId != null && !categoryId.isBlank()) {
                request.put("categoryId", categoryId);
            }

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(ragServiceUrl + "/api/faq-management/match-if-enabled"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode result = objectMapper.readTree(response.body());
                // Check if FAQ query is enabled and a match was found
                if (result.has("faqQueryEnabled") && !result.get("faqQueryEnabled").asBoolean()) {
                    log.debug("FAQ query is disabled, skipping FAQ cache");
                    return null;
                }
                return result;
            } else {
                log.debug("FAQ match check returned HTTP {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.debug("FAQ match check failed: {}", e.getMessage());
            return null;
        }
    }
}
