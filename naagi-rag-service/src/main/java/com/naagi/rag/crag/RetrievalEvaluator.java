package com.naagi.rag.crag;

import com.naagi.rag.llm.ChatClient;
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
            @Value("${naagi.rag.crag.llm-evaluation-enabled:false}") boolean llmEvaluationEnabled,
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

        // Base score from top result (most important)
        double baseScore = topScore;

        // Bonus for clear top result (large gap to second)
        double gapBonus = 0;
        if (topToSecondGap > scoreGapThreshold) {
            gapBonus = Math.min(0.1, topToSecondGap * 0.3);
        }

        // Penalty for high variance (inconsistent results)
        double variancePenalty = Math.min(0.2, variance * 0.5);

        // Bonus for consistent high scores
        double consistencyBonus = 0;
        if (avgScore > 0.6 && variance < 0.05) {
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

        String prompt = """
                You are evaluating the relevance of retrieved documents to a user's query.

                Query: %s

                Retrieved Documents:
                %s

                Rate the overall relevance of these documents to the query on a scale of 0 to 10:
                - 0-3: Documents are not relevant to the query
                - 4-6: Documents are somewhat relevant but may not fully answer the query
                - 7-10: Documents are highly relevant and likely contain the answer

                Respond with ONLY a single number from 0 to 10, nothing else.
                """.formatted(query, contextBuilder.toString());

        try {
            String response = chatClient.chatOnce(prompt, 0.1, 10);
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
