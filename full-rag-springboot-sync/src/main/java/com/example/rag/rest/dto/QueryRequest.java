package com.example.rag.rest.dto;

/**
 * Request DTO for query endpoint.
 * Contains the question to be answered and optional parameters for retrieval.
 */
public record QueryRequest(
    String question,
    Integer topK
) {
    /**
     * Default constructor with default topK value.
     */
    public QueryRequest(String question) {
        this(question, 5);
    }

    /**
     * Gets the topK value, defaulting to 5 if null.
     */
    public int getTopKOrDefault() {
        return topK != null ? topK : 5;
    }

    /**
     * Validates the query request.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question cannot be null or empty");
        }
        if (question.length() < 3) {
            throw new IllegalArgumentException("question is too short (minimum 3 characters)");
        }
        if (topK != null && (topK < 1 || topK > 20)) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
    }
}
