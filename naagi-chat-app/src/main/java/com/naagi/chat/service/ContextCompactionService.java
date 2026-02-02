package com.naagi.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.chat.config.ContextChatProperties;
import com.naagi.chat.entity.ChatMessageEntity;
import com.naagi.chat.entity.ChatSessionEntity;
import com.naagi.chat.repository.ChatMessageRepository;
import com.naagi.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for context-driven chat features:
 * - Reply-to context assembly: builds focused context from a referenced message
 * - Context compaction: summarizes older messages to free up context budget
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContextCompactionService {

    private final ContextChatProperties properties;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Value("${naagi.services.orchestrator.url:http://localhost:8086}")
    private String orchestratorUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Build focused context for reply-to mode.
     * Returns conversation history containing only the referenced Q&A pair
     * plus an optional session summary.
     */
    public List<ReplyToContext> buildReplyToContext(String sessionId, String replyToMessageId) {
        List<ReplyToContext> context = new ArrayList<>();

        // Load the referenced assistant message
        ChatMessageEntity referenced = messageRepository.findById(replyToMessageId).orElse(null);
        if (referenced == null) {
            log.warn("[CONTEXT] Reply-to message not found: {}", replyToMessageId);
            return context;
        }

        // Load session for summary
        ChatSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("[CONTEXT] Session not found: {}", sessionId);
            return context;
        }

        // Include session summary if available and configured
        if (properties.getReplyTo().isIncludeSessionSummary() && session.getContextSummary() != null) {
            context.add(new ReplyToContext("system",
                    "Previous conversation context: " + session.getContextSummary()));
        }

        // Find the user message that preceded the referenced answer
        List<ChatMessageEntity> previousUserMsgs = messageRepository.findPreviousUserMessages(
                sessionId, referenced.getTimestamp());
        if (!previousUserMsgs.isEmpty()) {
            context.add(new ReplyToContext("user", previousUserMsgs.get(0).getContent()));
        }

        // Add the referenced assistant message
        context.add(new ReplyToContext("assistant", referenced.getContent()));

        log.info("[CONTEXT] Built reply-to context for session {}: {} messages (replyTo={})",
                sessionId, context.size(), replyToMessageId);

        return context;
    }

    /**
     * Check if conversation context exceeds the compaction threshold.
     */
    public boolean needsCompaction(String sessionId) {
        if (!properties.getCompaction().isEnabled()) return false;

        ChatSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) return false;

        List<ChatMessageEntity> messages = messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);

        // Not enough messages to compact
        if (messages.size() <= properties.getCompaction().getKeepRecent()) return false;

        // Estimate token count for messages beyond the keep-recent window
        int totalTokens = estimateContextTokens(messages, session.getContextSummary());
        int threshold = (int) (properties.getCompaction().getMaxConversationTokens()
                * properties.getCompaction().getThreshold());

        boolean needed = totalTokens > threshold;
        if (needed) {
            log.info("[CONTEXT] Compaction needed for session {}: ~{} tokens > threshold {}",
                    sessionId, totalTokens, threshold);
        }
        return needed;
    }

    /**
     * Compact older messages into a summary. Returns the summary text.
     */
    @Transactional
    public String compactContext(String sessionId) {
        ChatSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("[CONTEXT] Cannot compact - session not found: {}", sessionId);
            return null;
        }

        List<ChatMessageEntity> messages = messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        int keepRecent = properties.getCompaction().getKeepRecent();

        if (messages.size() <= keepRecent) {
            log.debug("[CONTEXT] Not enough messages to compact in session {}", sessionId);
            return null;
        }

        // Partition: older messages to summarize, recent to keep
        List<ChatMessageEntity> toSummarize = messages.subList(0, messages.size() - keepRecent);
        ChatMessageEntity lastSummarized = toSummarize.get(toSummarize.size() - 1);

        // Build conversation text for summarization
        String conversationText = toSummarize.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String existingSummary = session.getContextSummary();

        // Call LLM to summarize
        String summary = callLlmForSummary(existingSummary, conversationText);

        if (summary == null) {
            log.warn("[CONTEXT] Compaction LLM call failed for session {}", sessionId);
            return null;
        }

        // Persist compaction result
        session.setContextSummary(summary);
        session.setSummarizedUpToMessageId(lastSummarized.getId());
        session.setApproximateContextTokens(estimateTokens(summary)
                + estimateTokensForMessages(messages.subList(messages.size() - keepRecent, messages.size())));
        sessionRepository.save(session);

        log.info("[CONTEXT] Compacted session {}: {} messages summarized into ~{} tokens",
                sessionId, toSummarize.size(), estimateTokens(summary));

        return summary;
    }

    /**
     * Get current context info for a session (for UI display).
     */
    public ContextInfo getContextInfo(String sessionId) {
        ChatSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) return new ContextInfo(0, 0, false, false);

        List<ChatMessageEntity> messages = messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        int totalTokens = estimateContextTokens(messages, session.getContextSummary());
        boolean hasSummary = session.getContextSummary() != null;
        boolean canCompact = messages.size() > properties.getCompaction().getKeepRecent();

        return new ContextInfo(messages.size(), totalTokens, hasSummary, canCompact);
    }

    /**
     * Call the LLM (via orchestrator's LLM endpoint) to generate a summary.
     */
    private String callLlmForSummary(String existingSummary, String conversationText) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are summarizing a conversation between a user and an AI assistant. ");
            prompt.append("Create a concise context paragraph that preserves:\n");
            prompt.append("- Key facts and specific values (job names, instance IDs, dates, counts, error messages)\n");
            prompt.append("- Decisions made or actions taken (tools called, results obtained)\n");
            prompt.append("- Entity names and their relationships\n");
            prompt.append("- Any unresolved questions or pending items\n\n");
            prompt.append("Do NOT include:\n");
            prompt.append("- Conversational greetings or pleasantries\n");
            prompt.append("- The assistant's reasoning process or hedging language\n");
            prompt.append("- Redundant information\n\n");

            if (existingSummary != null && !existingSummary.isBlank()) {
                prompt.append("Previous context summary:\n").append(existingSummary).append("\n\n");
                prompt.append("New conversation to incorporate:\n");
            } else {
                prompt.append("Conversation:\n");
            }
            prompt.append(conversationText).append("\n\n");
            prompt.append("Summary (keep under ").append(properties.getCompaction().getMaxSummaryTokens())
                    .append(" tokens):");

            // Use the LLM server directly for summarization (simpler than orchestrator)
            String llmServerUrl = orchestratorUrl.replace(":8086", ":8089");
            var requestBody = objectMapper.createObjectNode();
            requestBody.put("prompt", prompt.toString());
            requestBody.put("max_tokens", properties.getCompaction().getMaxSummaryTokens());

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(llmServerUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var responseNode = objectMapper.readTree(response.body());
                if (responseNode.has("response")) {
                    return responseNode.get("response").asText().trim();
                }
                // Try alternate field names
                if (responseNode.has("text")) {
                    return responseNode.get("text").asText().trim();
                }
                if (responseNode.has("content")) {
                    return responseNode.get("content").asText().trim();
                }
                log.warn("[CONTEXT] LLM response has no recognized text field: {}", response.body());
                return null;
            } else {
                log.warn("[CONTEXT] LLM summarization failed with HTTP {}: {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("[CONTEXT] Error calling LLM for summarization", e);
            return null;
        }
    }

    /**
     * Estimate total context tokens for a session's messages + optional summary.
     */
    private int estimateContextTokens(List<ChatMessageEntity> messages, String existingSummary) {
        int tokens = 0;
        if (existingSummary != null) {
            tokens += estimateTokens(existingSummary);
        }
        tokens += estimateTokensForMessages(messages);
        return tokens;
    }

    private int estimateTokensForMessages(List<ChatMessageEntity> messages) {
        return messages.stream()
                .mapToInt(m -> estimateTokens(m.getContent()) + 4) // +4 for role prefix overhead
                .sum();
    }

    /**
     * Approximate token count using ~4 chars/token heuristic.
     * Intentionally conservative (overestimates).
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }

    // DTO classes

    public record ReplyToContext(String role, String content) {}

    public record ContextInfo(int messageCount, int approximateTokens, boolean hasSummary, boolean canCompact) {}
}
