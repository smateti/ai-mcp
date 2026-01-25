package com.naag.rag.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid Search Service combining dense (semantic) and sparse (BM25) retrieval
 * using Reciprocal Rank Fusion (RRF).
 *
 * RRF is a rank-based fusion method that combines results from multiple retrieval
 * systems without requiring score normalization. It works by:
 * 1. Converting scores to ranks
 * 2. Computing RRF score as: 1/(k + rank) for each result list
 * 3. Summing RRF scores across all lists for each document
 *
 * Benefits:
 * - No need to normalize scores between different systems
 * - Robust to outliers
 * - Documents appearing in multiple lists get boosted
 */
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

    // RRF constant k (typically 60, can tune between 1-100)
    // Higher k = more equal weighting across ranks
    // Lower k = higher ranks get more weight
    private final double rrfK;

    public HybridSearchService() {
        this(60.0);
    }

    public HybridSearchService(double rrfK) {
        this.rrfK = rrfK;
    }

    /**
     * Generic search hit from any retrieval method
     */
    public record SearchHit(
            String id,
            String docId,
            int chunkIndex,
            String text,
            double score
    ) {}

    /**
     * Hybrid result with combined RRF score and original scores
     */
    public record HybridResult(
            String id,
            String docId,
            int chunkIndex,
            String text,
            double rrfScore,      // Combined RRF score
            double denseScore,    // Original dense/semantic score
            double sparseScore,   // Original sparse/BM25 score
            boolean inDense,      // Was found in dense results
            boolean inSparse      // Was found in sparse results
    ) {}

    /**
     * Reciprocal Rank Fusion (RRF)
     *
     * Formula: RRF(d) = Σ 1 / (k + rank(d))
     *
     * For each document d, sum 1/(k + rank) across all result lists
     * where rank is the 1-based position in that list.
     *
     * @param denseResults  Results from dense/semantic search (ordered by relevance)
     * @param sparseResults Results from sparse/BM25 search (ordered by relevance)
     * @param topK          Number of results to return
     * @return Fused and re-ranked results
     */
    public List<HybridResult> fuseWithRRF(
            List<SearchHit> denseResults,
            List<SearchHit> sparseResults,
            int topK) {

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, SearchHit> hitMap = new HashMap<>();
        Map<String, Double> denseScoreMap = new HashMap<>();
        Map<String, Double> sparseScoreMap = new HashMap<>();
        Set<String> inDenseSet = new HashSet<>();
        Set<String> inSparseSet = new HashSet<>();

        // Process dense results (rank is 1-based)
        for (int rank = 0; rank < denseResults.size(); rank++) {
            SearchHit hit = denseResults.get(rank);
            double rrfContribution = 1.0 / (rrfK + rank + 1);
            rrfScores.merge(hit.id(), rrfContribution, Double::sum);
            hitMap.putIfAbsent(hit.id(), hit);
            denseScoreMap.put(hit.id(), hit.score());
            inDenseSet.add(hit.id());
        }

        // Process sparse results
        for (int rank = 0; rank < sparseResults.size(); rank++) {
            SearchHit hit = sparseResults.get(rank);
            double rrfContribution = 1.0 / (rrfK + rank + 1);
            rrfScores.merge(hit.id(), rrfContribution, Double::sum);
            hitMap.putIfAbsent(hit.id(), hit);
            sparseScoreMap.put(hit.id(), hit.score());
            inSparseSet.add(hit.id());
        }

        // Sort by RRF score and take top K
        List<HybridResult> results = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    String id = entry.getKey();
                    SearchHit hit = hitMap.get(id);
                    return new HybridResult(
                            id,
                            hit.docId(),
                            hit.chunkIndex(),
                            hit.text(),
                            entry.getValue(),
                            denseScoreMap.getOrDefault(id, 0.0),
                            sparseScoreMap.getOrDefault(id, 0.0),
                            inDenseSet.contains(id),
                            inSparseSet.contains(id)
                    );
                })
                .collect(Collectors.toList());

        // Log fusion statistics
        long bothCount = results.stream().filter(r -> r.inDense() && r.inSparse()).count();
        long denseOnlyCount = results.stream().filter(r -> r.inDense() && !r.inSparse()).count();
        long sparseOnlyCount = results.stream().filter(r -> !r.inDense() && r.inSparse()).count();
        log.debug("RRF fusion: {} results (both={}, denseOnly={}, sparseOnly={})",
                results.size(), bothCount, denseOnlyCount, sparseOnlyCount);

        return results;
    }

    /**
     * Weighted RRF - allows tuning the balance between dense and sparse results
     *
     * @param denseResults  Results from dense search
     * @param sparseResults Results from sparse search
     * @param denseWeight   Weight for dense results (e.g., 0.7)
     * @param sparseWeight  Weight for sparse results (e.g., 0.3)
     * @param topK          Number of results to return
     * @return Fused results with weighted RRF scores
     */
    public List<HybridResult> fuseWithWeightedRRF(
            List<SearchHit> denseResults,
            List<SearchHit> sparseResults,
            double denseWeight,
            double sparseWeight,
            int topK) {

        // Normalize weights
        double totalWeight = denseWeight + sparseWeight;
        double normDenseWeight = denseWeight / totalWeight;
        double normSparseWeight = sparseWeight / totalWeight;

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, SearchHit> hitMap = new HashMap<>();
        Map<String, Double> denseScoreMap = new HashMap<>();
        Map<String, Double> sparseScoreMap = new HashMap<>();
        Set<String> inDenseSet = new HashSet<>();
        Set<String> inSparseSet = new HashSet<>();

        // Weighted dense contribution
        for (int rank = 0; rank < denseResults.size(); rank++) {
            SearchHit hit = denseResults.get(rank);
            double rrfContribution = normDenseWeight / (rrfK + rank + 1);
            rrfScores.merge(hit.id(), rrfContribution, Double::sum);
            hitMap.putIfAbsent(hit.id(), hit);
            denseScoreMap.put(hit.id(), hit.score());
            inDenseSet.add(hit.id());
        }

        // Weighted sparse contribution
        for (int rank = 0; rank < sparseResults.size(); rank++) {
            SearchHit hit = sparseResults.get(rank);
            double rrfContribution = normSparseWeight / (rrfK + rank + 1);
            rrfScores.merge(hit.id(), rrfContribution, Double::sum);
            hitMap.putIfAbsent(hit.id(), hit);
            sparseScoreMap.put(hit.id(), hit.score());
            inSparseSet.add(hit.id());
        }

        List<HybridResult> results = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    String id = entry.getKey();
                    SearchHit hit = hitMap.get(id);
                    return new HybridResult(
                            id,
                            hit.docId(),
                            hit.chunkIndex(),
                            hit.text(),
                            entry.getValue(),
                            denseScoreMap.getOrDefault(id, 0.0),
                            sparseScoreMap.getOrDefault(id, 0.0),
                            inDenseSet.contains(id),
                            inSparseSet.contains(id)
                    );
                })
                .collect(Collectors.toList());

        long bothCount = results.stream().filter(r -> r.inDense() && r.inSparse()).count();
        log.debug("Weighted RRF (dense={}, sparse={}): {} results, {} in both lists",
                normDenseWeight, normSparseWeight, results.size(), bothCount);

        return results;
    }

    /**
     * Linear score combination (alternative to RRF)
     *
     * Combines normalized scores: combined = α * dense_score + (1-α) * sparse_score
     *
     * Requires score normalization since dense (cosine: 0-1) and sparse (BM25: unbounded)
     * scores are on different scales.
     *
     * @param denseResults  Results from dense search
     * @param sparseResults Results from sparse search
     * @param alpha         Weight for dense scores (0-1)
     * @param topK          Number of results
     * @return Combined results
     */
    public List<HybridResult> fuseWithLinearCombination(
            List<SearchHit> denseResults,
            List<SearchHit> sparseResults,
            double alpha,
            int topK) {

        // Normalize scores to 0-1 range using min-max normalization
        Map<String, Double> normalizedDense = normalizeScores(denseResults);
        Map<String, Double> normalizedSparse = normalizeScores(sparseResults);

        Map<String, SearchHit> hitMap = new HashMap<>();
        Map<String, Double> denseScoreMap = new HashMap<>();
        Map<String, Double> sparseScoreMap = new HashMap<>();
        Set<String> inDenseSet = new HashSet<>();
        Set<String> inSparseSet = new HashSet<>();

        for (SearchHit hit : denseResults) {
            hitMap.putIfAbsent(hit.id(), hit);
            denseScoreMap.put(hit.id(), hit.score());
            inDenseSet.add(hit.id());
        }
        for (SearchHit hit : sparseResults) {
            hitMap.putIfAbsent(hit.id(), hit);
            sparseScoreMap.put(hit.id(), hit.score());
            inSparseSet.add(hit.id());
        }

        // Combine all document IDs
        Set<String> allIds = new HashSet<>();
        allIds.addAll(normalizedDense.keySet());
        allIds.addAll(normalizedSparse.keySet());

        // Calculate combined scores
        Map<String, Double> combinedScores = new HashMap<>();
        for (String id : allIds) {
            double denseNorm = normalizedDense.getOrDefault(id, 0.0);
            double sparseNorm = normalizedSparse.getOrDefault(id, 0.0);
            double combined = alpha * denseNorm + (1 - alpha) * sparseNorm;
            combinedScores.put(id, combined);
        }

        return combinedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    String id = entry.getKey();
                    SearchHit hit = hitMap.get(id);
                    return new HybridResult(
                            id,
                            hit.docId(),
                            hit.chunkIndex(),
                            hit.text(),
                            entry.getValue(),
                            denseScoreMap.getOrDefault(id, 0.0),
                            sparseScoreMap.getOrDefault(id, 0.0),
                            inDenseSet.contains(id),
                            inSparseSet.contains(id)
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Min-max normalize scores to 0-1 range
     */
    private Map<String, Double> normalizeScores(List<SearchHit> results) {
        if (results.isEmpty()) {
            return Map.of();
        }

        double minScore = results.stream().mapToDouble(SearchHit::score).min().orElse(0);
        double maxScore = results.stream().mapToDouble(SearchHit::score).max().orElse(1);
        double range = maxScore - minScore;

        if (range == 0) {
            // All scores are the same, normalize to 1.0
            return results.stream()
                    .collect(Collectors.toMap(SearchHit::id, h -> 1.0));
        }

        return results.stream()
                .collect(Collectors.toMap(
                        SearchHit::id,
                        h -> (h.score() - minScore) / range
                ));
    }

    public double getRrfK() {
        return rrfK;
    }
}
