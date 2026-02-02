package com.naagi.rag.crag;

import com.naagi.rag.crag.RetrievalEvaluator.ConfidenceCategory;
import com.naagi.rag.crag.RetrievalEvaluator.EvaluationResult;
import com.naagi.rag.llm.ChatClient;
import com.naagi.rag.llm.ChatMessage;
import com.naagi.rag.llm.ChatRequest;
import com.naagi.rag.llm.ChatResponse;
import com.naagi.rag.metrics.RagMetrics;
import com.naagi.rag.service.RagService;
import com.naagi.rag.service.RagService.QueryResult;
import com.naagi.rag.service.RagService.SourceChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Corrective RAG (CRAG) Service
 *
 * Implements the CRAG pattern to improve RAG quality by:
 * 1. Evaluating retrieval confidence
 * 2. Applying corrective strategies based on confidence level:
 *    - CORRECT: Use retrieved documents directly
 *    - AMBIGUOUS: Refine context, add uncertainty markers
 *    - INCORRECT: Apply fallback strategies (query expansion, alternative search)
 */
@Service
public class CragService {

    private static final Logger log = LoggerFactory.getLogger(CragService.class);

    private final RagService ragService;
    private final RetrievalEvaluator evaluator;
    private final ChatClient chatClient;
    private final RagMetrics metrics;
    private final boolean enabled;
    private final boolean queryExpansionEnabled;
    private final boolean knowledgeRefinementEnabled;
    private final int maxRetryAttempts;
    private final double minRelevanceForAnswer;

    public CragService(
            RagService ragService,
            RetrievalEvaluator evaluator,
            ChatClient chatClient,
            RagMetrics metrics,
            @Value("${naagi.rag.crag.enabled:true}") boolean enabled,
            @Value("${naagi.rag.crag.query-expansion-enabled:true}") boolean queryExpansionEnabled,
            @Value("${naagi.rag.crag.knowledge-refinement-enabled:true}") boolean knowledgeRefinementEnabled,
            @Value("${naagi.rag.crag.max-retry-attempts:2}") int maxRetryAttempts,
            @Value("${naagi.rag.crag.min-relevance-for-answer:0.7}") double minRelevanceForAnswer
    ) {
        this.ragService = ragService;
        this.evaluator = evaluator;
        this.chatClient = chatClient;
        this.metrics = metrics;
        this.enabled = enabled;
        this.queryExpansionEnabled = queryExpansionEnabled;
        this.knowledgeRefinementEnabled = knowledgeRefinementEnabled;
        this.maxRetryAttempts = maxRetryAttempts;
        this.minRelevanceForAnswer = minRelevanceForAnswer;

        log.info("[CRAG] CragService initialized: enabled={}, queryExpansion={}, knowledgeRefinement={}, maxRetries={}, minRelevanceForAnswer={}",
                enabled, queryExpansionEnabled, knowledgeRefinementEnabled, maxRetryAttempts, minRelevanceForAnswer);
    }

    /**
     * Extended query result with CRAG metadata
     */
    public record CragQueryResult(
            String question,
            String answer,
            List<SourceChunk> sources,
            CragMetadata cragMetadata
    ) {
        public QueryResult toQueryResult() {
            return new QueryResult(question, answer, sources);
        }
    }

    /**
     * CRAG-specific metadata
     */
    public record CragMetadata(
            double confidenceScore,
            ConfidenceCategory category,
            String evaluationReason,
            List<String> appliedStrategies,
            int retriesPerformed,
            String originalQuery,
            List<String> expandedQueries,
            boolean knowledgeRefined
    ) {}

    /**
     * Main CRAG query method
     */
    public CragQueryResult askWithCrag(String question, int topK, String category) {
        if (!enabled) {
            log.debug("[CRAG] CRAG disabled, falling back to standard RAG");
            QueryResult result = ragService.askWithReranking(question, topK, category);
            return new CragQueryResult(
                    result.question(),
                    result.answer(),
                    result.sources(),
                    new CragMetadata(1.0, ConfidenceCategory.CORRECT, "CRAG disabled",
                            List.of(), 0, question, List.of(), false)
            );
        }

        long startTime = System.currentTimeMillis();
        List<String> appliedStrategies = new ArrayList<>();
        List<String> expandedQueries = new ArrayList<>();
        int retries = 0;

        // Initial retrieval
        List<SourceChunk> sources = ragService.searchWithReranking(question, topK, category);
        EvaluationResult evaluation = evaluator.evaluate(question, sources);

        log.info("[CRAG] Initial evaluation: confidence={:.3f}, category={}",
                evaluation.confidenceScore(), evaluation.category());

        // Apply corrective strategies based on confidence category
        switch (evaluation.category()) {
            case CORRECT -> {
                appliedStrategies.add("direct_use");
                // High confidence - use results directly
            }

            case AMBIGUOUS -> {
                appliedStrategies.add("ambiguous_handling");

                // Try knowledge refinement
                if (knowledgeRefinementEnabled) {
                    sources = refineKnowledge(question, sources);
                    appliedStrategies.add("knowledge_refinement");
                }

                // Re-evaluate after refinement
                evaluation = evaluator.evaluate(question, sources);
            }

            case INCORRECT -> {
                appliedStrategies.add("correction_triggered");

                // Try query expansion
                if (queryExpansionEnabled && retries < maxRetryAttempts) {
                    List<String> expanded = expandQuery(question);
                    expandedQueries.addAll(expanded);

                    for (String expandedQuery : expanded) {
                        retries++;
                        List<SourceChunk> newSources = ragService.searchWithReranking(expandedQuery, topK, category);
                        EvaluationResult newEval = evaluator.evaluate(expandedQuery, newSources);

                        log.info("[CRAG] Retry {} with expanded query '{}': confidence={:.3f}",
                                retries, expandedQuery, newEval.confidenceScore());

                        if (newEval.confidenceScore() > evaluation.confidenceScore()) {
                            sources = newSources;
                            evaluation = newEval;
                            appliedStrategies.add("query_expansion_success");

                            if (evaluation.category() != ConfidenceCategory.INCORRECT) {
                                break; // Good enough, stop retrying
                            }
                        }

                        if (retries >= maxRetryAttempts) break;
                    }
                }

                // If still low confidence, try merging all results
                if (evaluation.category() == ConfidenceCategory.INCORRECT && !expandedQueries.isEmpty()) {
                    sources = mergeAndDeduplicateSources(question, sources, expandedQueries, topK, category);
                    evaluation = evaluator.evaluate(question, sources);
                    appliedStrategies.add("source_merging");
                }
            }
        }

        // Check if we should refuse to answer due to low relevance
        double topRelevanceScore = sources.isEmpty() ? 0.0 : sources.get(0).relevanceScore();
        String answer;

        // If top relevance is too low, don't even ask LLM - it will hallucinate
        if (topRelevanceScore < minRelevanceForAnswer && evaluation.category() != ConfidenceCategory.CORRECT) {
            answer = "I don't have specific information about that in the knowledge base. " +
                     "The retrieved documents discuss related topics but don't directly address your question.";
            appliedStrategies.add("low_relevance_refusal");
            log.info("[CRAG] Refusing to generate answer due to low relevance: topScore={}", topRelevanceScore);
        } else {
            // Generate answer with appropriate context
            answer = generateAnswer(question, sources, evaluation);

            // Add uncertainty marker if still ambiguous
            if (evaluation.category() == ConfidenceCategory.AMBIGUOUS) {
                answer = addUncertaintyMarker(answer);
                appliedStrategies.add("uncertainty_marker_added");
            }

            // Add no-information indicator if still incorrect
            if (evaluation.category() == ConfidenceCategory.INCORRECT &&
                    (sources.isEmpty() || evaluation.confidenceScore() < 0.3)) {
                answer = "I couldn't find highly relevant information to answer this question. " + answer;
                appliedStrategies.add("low_confidence_disclaimer");
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[CRAG] Completed in {}ms: confidence={:.3f}, category={}, strategies={}",
                totalTime, evaluation.confidenceScore(), evaluation.category(), appliedStrategies);

        // Record metrics
        metrics.recordCragQuery(
                totalTime,
                evaluation.category().name(),
                retries,
                !expandedQueries.isEmpty()
        );

        CragMetadata metadata = new CragMetadata(
                evaluation.confidenceScore(),
                evaluation.category(),
                evaluation.reason(),
                appliedStrategies,
                retries,
                question,
                expandedQueries,
                appliedStrategies.contains("knowledge_refinement")
        );

        return new CragQueryResult(question, answer, sources, metadata);
    }

    /**
     * Expand a query into multiple related queries for broader retrieval
     */
    private List<String> expandQuery(String originalQuery) {
        String systemMsg = """
                You are helping improve a search query. Given the original query, generate 2-3 alternative \
                phrasings or related queries that might help find relevant information.

                Generate alternative queries, one per line. Do not include numbering or bullets.
                Focus on:
                1. Rephrasing with different keywords
                2. More specific versions of the query
                3. Related concepts that might contain the answer

                Respond with ONLY the alternative queries, one per line, nothing else.""";

        try {
            ChatResponse resp = chatClient.chat(ChatRequest.of(
                    List.of(ChatMessage.system(systemMsg), ChatMessage.user(originalQuery)),
                    0.7, 150));
            String response = resp.content();
            List<String> expanded = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && s.length() > 5)
                    .limit(3)
                    .toList();

            log.debug("[CRAG] Query expansion: '{}' -> {}", originalQuery, expanded);
            return expanded;
        } catch (Exception e) {
            log.warn("[CRAG] Query expansion failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Refine knowledge by filtering and prioritizing relevant chunks
     */
    private List<SourceChunk> refineKnowledge(String query, List<SourceChunk> sources) {
        if (sources.size() <= 2) {
            return sources; // Not enough to refine
        }

        // Score each chunk for relevance to the query
        List<ScoredChunk> scoredChunks = new ArrayList<>();

        for (SourceChunk chunk : sources) {
            double relevanceBoost = calculateChunkRelevance(query, chunk.text());
            double combinedScore = chunk.relevanceScore() * 0.7 + relevanceBoost * 0.3;
            scoredChunks.add(new ScoredChunk(chunk, combinedScore));
        }

        // Sort by combined score and return top chunks
        return scoredChunks.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(sources.size())
                .map(sc -> new SourceChunk(
                        sc.chunk.docId(),
                        sc.chunk.chunkIndex(),
                        sc.score, // Use refined score
                        sc.chunk.text(),
                        sc.chunk.title(),
                        sc.chunk.systemPrompt()
                ))
                .toList();
    }

    private record ScoredChunk(SourceChunk chunk, double score) {}

    /**
     * Calculate relevance of a chunk to the query using keyword matching
     */
    private double calculateChunkRelevance(String query, String chunkText) {
        String[] queryWords = query.toLowerCase().split("\\s+");
        String lowerChunk = chunkText.toLowerCase();

        int matches = 0;
        for (String word : queryWords) {
            if (word.length() > 3 && lowerChunk.contains(word)) {
                matches++;
            }
        }

        return queryWords.length > 0 ? (double) matches / queryWords.length : 0.0;
    }

    /**
     * Merge and deduplicate sources from multiple queries
     */
    private List<SourceChunk> mergeAndDeduplicateSources(String originalQuery,
            List<SourceChunk> originalSources, List<String> expandedQueries,
            int topK, String category) {

        Map<String, SourceChunk> uniqueChunks = new LinkedHashMap<>();

        // Add original sources
        for (SourceChunk chunk : originalSources) {
            String key = chunk.docId() + ":" + chunk.chunkIndex();
            uniqueChunks.put(key, chunk);
        }

        // Add sources from expanded queries
        for (String expandedQuery : expandedQueries) {
            List<SourceChunk> expandedSources = ragService.searchWithReranking(expandedQuery, topK, category);
            for (SourceChunk chunk : expandedSources) {
                String key = chunk.docId() + ":" + chunk.chunkIndex();
                if (!uniqueChunks.containsKey(key)) {
                    uniqueChunks.put(key, chunk);
                } else {
                    // Update score if higher
                    SourceChunk existing = uniqueChunks.get(key);
                    if (chunk.relevanceScore() > existing.relevanceScore()) {
                        uniqueChunks.put(key, chunk);
                    }
                }
            }
        }

        // Sort by score and limit
        return uniqueChunks.values().stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()))
                .limit(topK)
                .toList();
    }

    /**
     * Generate answer using the LLM with context.
     * Uses ICE method (Instructions, Constraints, Escalation) and post-generation grounding check.
     */
    private String generateAnswer(String question, List<SourceChunk> sources, EvaluationResult evaluation) {
        if (sources.isEmpty()) {
            return "I don't have information about that in the knowledge base.";
        }

        String contextBlock = sources.stream()
                .map(SourceChunk::text)
                .collect(Collectors.joining("\n\n---\n\n"));

        // Resolve prompt template: use document-specific template if available, otherwise default
        String template = com.naagi.rag.service.RagService.resolvePromptTemplate(sources);
        String prompt = com.naagi.rag.service.RagService.buildPromptFromTemplate(template, contextBlock, question);

        // Use very low temperature (0.1) for deterministic, conservative responses
        ChatResponse resp = chatClient.chat(ChatRequest.of(
                List.of(ChatMessage.system("You are a documentation assistant. Answer ONLY using information explicitly stated in the provided context."),
                        ChatMessage.user(prompt)),
                0.1, 256));
        String answer = resp.content();

        // Post-generation grounding check: detect potential hallucinations
        answer = validateAndFilterHallucinations(answer, contextBlock, question);

        return answer;
    }

    /**
     * Post-generation grounding check.
     * Detects potential hallucinated technical terms (method names, class names, etc.)
     * that don't appear in the source context.
     */
    private String validateAndFilterHallucinations(String answer, String context, String question) {
        // If answer already indicates no information, return as-is
        if (answer.toLowerCase().contains("don't have") ||
            answer.toLowerCase().contains("no information") ||
            answer.toLowerCase().contains("not found") ||
            answer.toLowerCase().contains("doesn't address")) {
            return answer;
        }

        // Extract potential technical terms from the answer (camelCase, PascalCase, method calls)
        java.util.regex.Pattern technicalTermPattern = java.util.regex.Pattern.compile(
            "\\b([A-Z][a-z]+[A-Z][a-zA-Z]*|[a-z]+[A-Z][a-zA-Z]*|\\w+\\(\\)|\\w+\\.\\w+)\\b"
        );
        java.util.regex.Matcher matcher = technicalTermPattern.matcher(answer);

        java.util.Set<String> suspiciousTerms = new java.util.HashSet<>();
        String contextLower = context.toLowerCase();

        while (matcher.find()) {
            String term = matcher.group(1);
            // Check if this technical term exists in the context
            if (!contextLower.contains(term.toLowerCase()) &&
                !contextLower.contains(term.replace("()", "").toLowerCase())) {
                suspiciousTerms.add(term);
            }
        }

        // If we found suspicious technical terms that don't exist in context, likely hallucination
        if (!suspiciousTerms.isEmpty()) {
            log.warn("[CRAG] Potential hallucination detected. Terms not in context: {}. Replacing answer.",
                    suspiciousTerms);

            // Return a safe response instead
            return "I don't have specific information about that in the knowledge base. " +
                   "The available documentation covers related topics but doesn't contain " +
                   "the specific technical details you're asking about.";
        }

        return answer;
    }

    /**
     * Add uncertainty marker to answer for ambiguous results
     */
    private String addUncertaintyMarker(String answer) {
        if (answer.startsWith("I don't") || answer.startsWith("I couldn't")) {
            return answer;
        }

        // Check if answer already has uncertainty language
        String lowerAnswer = answer.toLowerCase();
        if (lowerAnswer.contains("may ") || lowerAnswer.contains("might ") ||
                lowerAnswer.contains("possibly") || lowerAnswer.contains("it appears")) {
            return answer;
        }

        return "Based on the available information: " + answer;
    }

    /**
     * Check if CRAG is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get CRAG configuration stats
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("enabled", enabled);
        stats.put("queryExpansionEnabled", queryExpansionEnabled);
        stats.put("knowledgeRefinementEnabled", knowledgeRefinementEnabled);
        stats.put("maxRetryAttempts", maxRetryAttempts);
        stats.put("minRelevanceForAnswer", minRelevanceForAnswer);
        return stats;
    }
}
