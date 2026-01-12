package com.example.rag.rest.dto;

/**
 * Response DTO for health check endpoint.
 * Contains status information about the RAG system components.
 */
public record HealthResponse(
    String status,
    boolean qdrantConnected,
    boolean llmProviderConnected,
    boolean collectionExists,
    String details
) {
    /**
     * Creates a healthy response.
     */
    public static HealthResponse healthy(boolean qdrantConnected, boolean llmProviderConnected, boolean collectionExists) {
        String status = (qdrantConnected && llmProviderConnected && collectionExists) ? "healthy" : "degraded";
        StringBuilder details = new StringBuilder();

        if (!qdrantConnected) {
            details.append("Qdrant not connected. ");
        }
        if (!llmProviderConnected) {
            details.append("LLM provider not connected. ");
        }
        if (!collectionExists) {
            details.append("Qdrant collection does not exist. ");
        }

        return new HealthResponse(
            status,
            qdrantConnected,
            llmProviderConnected,
            collectionExists,
            details.toString().trim()
        );
    }

    /**
     * Creates an unhealthy response.
     */
    public static HealthResponse unhealthy(String errorDetails) {
        return new HealthResponse(
            "unhealthy",
            false,
            false,
            false,
            errorDetails
        );
    }
}
