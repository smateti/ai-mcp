package com.naagi.rag.crag;

import com.naagi.rag.llm.ChatClient;
import com.naagi.rag.llm.ChatMessage;
import com.naagi.rag.llm.ChatRequest;
import com.naagi.rag.llm.ChatResponse;
import com.naagi.rag.service.RagService.SourceChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Evaluates the relevance of retrieved documents to the query.
 * Part of the Corrective RAG (CRAG) implementation.
 *
 * Uses multiple signals to compute a confidence score:
 * 1. Top score analysis - highest relevance score
 * 2. Score distribution - variance and gaps between scores
 * 3. Optional LLM-based verification for borderline cases
 */
@Component
public class RetrievalEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluator.class);

    private final ChatClient chatClient;
    private final boolean llmEvaluationEnabled;
    private final double highConfidenceThreshold;
    private final double lowConfidenceThreshold;
    private final double scoreGapThreshold;

    public RetrievalEvaluator(
            ChatClient chatClient,
            @Value("${naagi.rag.crag.llm-evaluation-enabled:true}") boolean llmEvaluationEnabled,
            @Value("${naagi.rag.crag.high-confidence-threshold:0.8}") double highConfidenceThreshold,
            @Value("${naagi.rag.crag.low-confidence-threshold:0.5}") double lowConfidenceThreshold,
            @Value("${naagi.rag.crag.score-gap-threshold:0.15}") double scoreGapThreshold
    ) {
        this.chatClient = chatClient;
        this.llmEvaluationEnabled = llmEvaluationEnabled;
        this.highConfidenceThreshold = highConfidenceThreshold;
        this.lowConfidenceThreshold = lowConfidenceThreshold;
        this.scoreGapThreshold = scoreGapThreshold;

        log.info("[CRAG] RetrievalEvaluator initialized: llmEval={}, highThreshold={}, lowThreshold={}, gapThreshold={}",
                llmEvaluationEnabled, highConfidenceThreshold, lowConfidenceThreshold, scoreGapThreshold);
    }

    /**
     * Evaluation result containing confidence score and category
     */
    public record EvaluationResult(
            double confidenceScore,      // 0.0 to 1.0
            ConfidenceCategory category, // CORRECT, AMBIGUOUS, INCORRECT
            String reason,               // Human-readable reason
            EvaluationMetrics metrics    // Detailed metrics
    ) {}

    /**
     * Detailed metrics from the evaluation
     */
    public record EvaluationMetrics(
            double topScore,
            double averageScore,
            double scoreVariance,
            double topToSecondGap,
            int totalResults,
            Double llmScore // null if LLM evaluation not used
    ) {}

    /**
     * Confidence categories based on CRAG paper
     */
    public enum ConfidenceCategory {
        CORRECT,    // High confidence - use retrieved documents directly
        AMBIGUOUS,  // Medium confidence - refine search or add disclaimer
        INCORRECT   // Low confidence - trigger fallback strategies
    }

    /**
     * Evaluate the quality of retrieved results for a given query
     */
    public EvaluationResult evaluate(String query, List<SourceChunk> results) {
        if (results == null || results.isEmpty()) {
            return new EvaluationResult(
                    0.0,
                    ConfidenceCategory.INCORRECT,
                    "No results retrieved",
                    new EvaluationMetrics(0, 0, 0, 0, 0, null)
            );
        }

        // Calculate basic metrics
        double topScore = results.get(0).relevanceScore();
        double avgScore = results.stream()
                .mapToDouble(SourceChunk::relevanceScore)
                .average()
                .orElse(0.0);
        double variance = calculateVariance(results);
        double topToSecondGap = results.size() > 1
                ? topScore - results.get(1).relevanceScore()
                : topScore;

        // Calculate heuristic confidence score
        double heuristicScore = calculateHeuristicConfidence(topScore, avgScore, variance, topToSecondGap, results.size());

        // Optionally use LLM for borderline cases
        Double llmScore = null;
        if (llmEvaluationEnabled && isAmbiguousScore(heuristicScore)) {
            llmScore = evaluateWithLLM(query, results);
            log.debug("[CRAG] LLM evaluation score: {}", llmScore);
        }

        // Combine scores
        double finalScore = llmScore != null
                ? (heuristicScore * 0.6 + llmScore * 0.4) // Weighted combination
                : heuristicScore;

        // Determine category
        ConfidenceCategory category = categorize(finalScore);
        String reason = buildReason(topScore, avgScore, variance, category, llmScore);

        EvaluationMetrics metrics = new EvaluationMetrics(
                topScore, avgScore, variance, topToSecondGap, results.size(), llmScore
        );

        log.info("[CRAG] Evaluation: confidence={:.3f}, category={}, topScore={:.3f}, avgScore={:.3f}, variance={:.4f}",
                finalScore, category, topScore, avgScore, variance);

        return new EvaluationResult(finalScore, category, reason, metrics);
    }

    /**
     * Calculate heuristic confidence based on score metrics
     */
    private double calculateHeuristicConfidence(double topScore, double avgScore,
            double variance, double topToSecondGap, int resultCount) {

        // Normalize scores to 0-1 range (reranker scores can be > 1)
        // Use sigmoid for smooth normalization: scores around 0.5-1.0 map to ~0.6-0.7
        // High reranker scores (3+) will map to ~0.95, but this alone shouldn't mean CORRECT
        double normalizedTopScore = normalizeScore(topScore);
        double normalizedAvg = normalizeScore(avgScore);

        // Base score from normalized top result
        double baseScore = normalizedTopScore;

        // Bonus for clear top result (large gap to second)
        double gapBonus = 0;
        if (topToSecondGap > scoreGapThreshold) {
            gapBonus = Math.min(0.1, topToSecondGap * 0.3);
        }

        // Penalty for high variance (inconsistent results)
        double variancePenalty = Math.min(0.2, variance * 0.5);

        // Bonus for consistent high scores (use normalized avg)
        double consistencyBonus = 0;
        if (normalizedAvg > 0.6 && variance < 0.05) {
            consistencyBonus = 0.05;
        }

        // Penalty for too few results (might miss relevant docs)
        double countPenalty = resultCount < 3 ? 0.05 : 0;

        double confidence = baseScore + gapBonus - variancePenalty + consistencyBonus - countPenalty;

        // Clamp to [0, 1]
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Calculate variance of relevance scores
     */
    private double calculateVariance(List<SourceChunk> results) {
        if (results.size() < 2) return 0.0;

        double mean = results.stream()
                .mapToDouble(SourceChunk::relevanceScore)
                .average()
                .orElse(0.0);

        return results.stream()
                .mapToDouble(r -> Math.pow(r.relevanceScore() - mean, 2))
                .average()
                .orElse(0.0);
    }

    /**
     * Normalize relevance scores to 0-1 range.
     * Reranker scores can be much larger than 1, so we use sigmoid normalization.
     * This ensures scores like 3.7 don't automatically become CORRECT.
     *
     * Conservative mapping to ensure LLM evaluation is triggered:
     * - score 0.5 -> ~0.5
     * - score 1.0 -> ~0.5
     * - score 2.0 -> ~0.62
     * - score 3.0 -> ~0.73
     * - score 4.0 -> ~0.82
     * - score 5.0 -> ~0.88
     *
     * This means reranker scores need to be very high (5+) to be CORRECT without LLM check.
     */
    private double normalizeScore(double score) {
        if (score <= 0) return 0.0;
        if (score <= 1.0) return score * 0.5; // Compress 0-1 similarity scores to 0-0.5
        // For reranker scores > 1, use conservative sigmoid
        // Shift by 3 so that scores need to be very high to exceed threshold
        return 1.0 / (1.0 + Math.exp(-score + 3));
    }

    /**
     * Check if score is in ambiguous range requiring LLM evaluation
     */
    private boolean isAmbiguousScore(double score) {
        return score >= lowConfidenceThreshold && score < highConfidenceThreshold;
    }

    /**
     * Use LLM to evaluate relevance of top results to query
     */
    private double evaluateWithLLM(String query, List<SourceChunk> results) {
        // Take top 3 results for evaluation
        List<SourceChunk> topResults = results.stream().limit(3).toList();

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < topResults.size(); i++) {
            contextBuilder.append("Document ").append(i + 1).append(":\n");
            contextBuilder.append(topResults.get(i).text().substring(0,
                    Math.min(500, topResults.get(i).text().length())));
            contextBuilder.append("\n\n");
        }

        String systemMsg = """
                You are evaluating whether retrieved documents can DIRECTLY ANSWER a user's specific question.

                IMPORTANT: Rate whether these documents contain EXPLICIT information to answer the SPECIFIC question asked.
                Do NOT give a high score just because the documents are about a related topic.

                Rate on a scale of 0 to 10:
                - 0-3: Documents do NOT contain information that answers this specific question
                - 4-6: Documents contain partially relevant info but may not fully answer the specific question
                - 7-10: Documents contain EXPLICIT information that DIRECTLY answers the specific question

                Example: If asked "How to call function X asynchronously?" but documents only discuss general batch processing without mentioning async calls to function X, score should be 0-3.

                Respond with ONLY a single number from 0 to 10, nothing else.""";

        String userMsg = "Question: %s\n\nRetrieved Documents:\n%s".formatted(query, contextBuilder.toString());

        try {
            ChatResponse resp = chatClient.chat(ChatRequest.of(
                    java.util.List.of(ChatMessage.system(systemMsg), ChatMessage.user(userMsg)),
                    0.1, 10));
            String response = resp.content();
            double score = Double.parseDouble(response.trim());
            return Math.max(0.0, Math.min(1.0, score / 10.0));
        } catch (Exception e) {
            log.warn("[CRAG] LLM evaluation failed: {}", e.getMessage());
            return 0.5; // Default to neutral score on failure
        }
    }

    /**
     * Categorize based on confidence score
     */
    private ConfidenceCategory categorize(double score) {
        if (score >= highConfidenceThreshold) {
            return ConfidenceCategory.CORRECT;
        } else if (score >= lowConfidenceThreshold) {
            return ConfidenceCategory.AMBIGUOUS;
        } else {
            return ConfidenceCategory.INCORRECT;
        }
    }

    /**
     * Build human-readable reason for the evaluation
     */
    private String buildReason(double topScore, double avgScore, double variance,
            ConfidenceCategory category, Double llmScore) {

        StringBuilder reason = new StringBuilder();

        reason.append("Top relevance score: ").append(String.format("%.3f", topScore));
        reason.append(", Average: ").append(String.format("%.3f", avgScore));

        if (variance > 0.05) {
            reason.append(" (high variance indicates inconsistent results)");
        }

        if (llmScore != null) {
            reason.append(", LLM verification: ").append(String.format("%.1f/10", llmScore * 10));
        }

        switch (category) {
            case CORRECT -> reason.append(" - Retrieved documents appear highly relevant.");
            case AMBIGUOUS -> reason.append(" - Relevance is uncertain, results may be partially applicable.");
            case INCORRECT -> reason.append(" - Retrieved documents do not appear relevant to the query.");
        }

        return reason.toString();
    }
}
