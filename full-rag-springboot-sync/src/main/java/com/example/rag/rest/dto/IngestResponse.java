package com.example.rag.rest.dto;

/**
 * Response DTO for document ingestion endpoint.
 * Contains the result of the ingestion operation including success status and chunk count.
 */
public record IngestResponse(
    boolean success,
    String docId,
    int chunksCreated,
    String message
) {
    /**
     * Creates a successful ingestion response.
     */
    public static IngestResponse success(String docId, int chunksCreated) {
        return new IngestResponse(
            true,
            docId,
            chunksCreated,
            String.format("Document '%s' ingested successfully with %d chunks", docId, chunksCreated)
        );
    }

    /**
     * Creates an error response.
     */
    public static IngestResponse error(String docId, String errorMessage) {
        return new IngestResponse(
            false,
            docId,
            0,
            errorMessage
        );
    }
}
