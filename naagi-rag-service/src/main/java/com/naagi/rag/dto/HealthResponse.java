package com.naagi.rag.dto;

public record HealthResponse(
        String status,
        boolean qdrantConnected,
        boolean llmProviderConnected,
        boolean collectionExists,
        String errorMessage
) {
    public static HealthResponse healthy(boolean qdrant, boolean llm, boolean collection) {
        String status = (qdrant && llm && collection) ? "healthy" : "degraded";
        return new HealthResponse(status, qdrant, llm, collection, null);
    }

    public static HealthResponse unhealthy(String errorMessage) {
        return new HealthResponse("unhealthy", false, false, false, errorMessage);
    }
}
