package com.naag.rag.dto;

public record QueryRequest(
        String question,
        Integer topK,
        String category
) {
    private static final int DEFAULT_TOP_K = 5;

    public void validate() {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question is required");
        }
    }

    public int getTopKOrDefault() {
        return topK != null && topK > 0 ? topK : DEFAULT_TOP_K;
    }
}
