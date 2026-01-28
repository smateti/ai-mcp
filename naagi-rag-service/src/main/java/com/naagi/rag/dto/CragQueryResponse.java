package com.naagi.rag.dto;

import java.util.List;

/**
 * Response DTO for CRAG (Corrective RAG) queries.
 * Extends standard QueryResponse with CRAG-specific metadata.
 */
public record CragQueryResponse(
        boolean success,
        String question,
        String answer,
        List<SourceMetadata> sources,
        String errorMessage,

        // CRAG-specific fields
        double confidenceScore,           // 0.0 to 1.0
        String confidenceCategory,        // CORRECT, AMBIGUOUS, INCORRECT
        String evaluationReason,          // Human-readable evaluation reason
        List<String> appliedStrategies,   // Strategies applied (e.g., "query_expansion", "knowledge_refinement")
        int retriesPerformed,             // Number of retry attempts
        List<String> expandedQueries      // Queries generated during expansion
) {
    public static CragQueryResponse error(String question, String errorMessage) {
        return new CragQueryResponse(
                false,
                question,
                null,
                List.of(),
                errorMessage,
                0.0,
                "INCORRECT",
                "Error occurred",
                List.of(),
                0,
                List.of()
        );
    }
}
