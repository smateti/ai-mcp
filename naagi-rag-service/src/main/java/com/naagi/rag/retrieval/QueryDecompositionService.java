package com.naagi.rag.retrieval;

import com.naagi.rag.llm.ChatClient;
import com.naagi.rag.llm.ChatMessage;
import com.naagi.rag.llm.ChatRequest;
import com.naagi.rag.llm.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decomposes complex multi-faceted questions into simpler sub-queries.
 *
 * Complex questions like "What are batch jobs and how do log searches work?"
 * are split into independent sub-queries that are each answered via RAG,
 * then the contexts are merged for a comprehensive final answer.
 */
@Service
public class QueryDecompositionService {

    private static final Logger log = LoggerFactory.getLogger(QueryDecompositionService.class);

    private final ChatClient chatClient;
    private final boolean enabled;
    private final int maxSubQueries;

    private static final String DECOMPOSE_PROMPT = """
            Analyze this question and determine if it asks about MULTIPLE distinct topics.
            If it does, break it into separate, self-contained sub-questions (max %d).
            If it's a single focused question, return it as-is.

            RULES:
            - Each sub-question must be self-contained and understandable on its own
            - Do NOT split questions that are about one topic (even if complex)
            - Only split when there are genuinely different topics joined by "and", "also", "additionally", etc.
            - Output ONLY the questions, one per line, prefixed with "Q: "

            Question: %s

            Sub-questions:""";

    // Heuristic patterns that suggest multi-topic questions
    private static final Pattern MULTI_TOPIC_PATTERN = Pattern.compile(
            "(?i)(\\band\\b.*\\b(how|what|why|when|where|which|explain|describe)\\b)" +
            "|(\\balso\\b)" +
            "|(\\badditionally\\b)" +
            "|(\\?.*\\?)");

    public QueryDecompositionService(
            ChatClient chatClient,
            @Value("${naagi.rag.query-decomposition.enabled:false}") boolean enabled,
            @Value("${naagi.rag.query-decomposition.max-sub-queries:3}") int maxSubQueries) {
        this.chatClient = chatClient;
        this.enabled = enabled;
        this.maxSubQueries = maxSubQueries;
        log.info("[QUERY-DECOMP] Initialized, enabled={}, maxSubQueries={}", enabled, maxSubQueries);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if a question likely needs decomposition (cheap heuristic check before LLM call).
     */
    public boolean likelyNeedsDecomposition(String question) {
        if (!enabled || question == null || question.length() < 30) return false;
        return MULTI_TOPIC_PATTERN.matcher(question).find();
    }

    /**
     * Decompose a complex question into sub-queries using LLM.
     * Returns a single-element list if the question doesn't need decomposition.
     */
    public List<String> decompose(String question) {
        if (!enabled) {
            return List.of(question);
        }

        // Quick heuristic check — avoid LLM call for simple questions
        if (!likelyNeedsDecomposition(question)) {
            log.debug("[QUERY-DECOMP] Heuristic says single-topic, skipping LLM: {}", question);
            return List.of(question);
        }

        try {
            long start = System.currentTimeMillis();

            String prompt = DECOMPOSE_PROMPT.formatted(maxSubQueries, question);
            ChatResponse response = chatClient.chat(ChatRequest.of(
                    List.of(
                            ChatMessage.system("You are a query analysis assistant. Break down complex questions into simpler sub-questions."),
                            ChatMessage.user(prompt)
                    ),
                    0.1, 256));

            String output = response.content();
            if (output == null || output.isBlank()) {
                return List.of(question);
            }

            List<String> subQueries = parseSubQueries(output);

            long duration = System.currentTimeMillis() - start;

            if (subQueries.size() <= 1) {
                log.debug("[QUERY-DECOMP] LLM determined single-topic ({}ms): {}", duration, question);
                return List.of(question);
            }

            log.info("[QUERY-DECOMP] Decomposed into {} sub-queries ({}ms): {}",
                    subQueries.size(), duration, subQueries);
            return subQueries;

        } catch (Exception e) {
            log.warn("[QUERY-DECOMP] Failed ({}), using original query", e.getMessage());
            return List.of(question);
        }
    }

    private List<String> parseSubQueries(String output) {
        List<String> queries = new ArrayList<>();
        for (String line : output.split("\n")) {
            line = line.trim();
            // Strip "Q: ", "1. ", "- ", etc.
            if (line.startsWith("Q:")) line = line.substring(2).trim();
            else if (line.matches("^\\d+\\.\\s.*")) line = line.replaceFirst("^\\d+\\.\\s*", "");
            else if (line.startsWith("- ")) line = line.substring(2).trim();

            if (!line.isBlank() && line.length() > 10) {
                queries.add(line);
                if (queries.size() >= maxSubQueries) break;
            }
        }
        return queries.isEmpty() ? List.of(output.trim()) : queries;
    }
}
