package com.example.rag.rest.dto;

/**
 * Request DTO for document ingestion endpoint.
 * Contains the document ID and full text content to be ingested into the RAG system.
 */
public record IngestRequest(
    String docId,
    String text
) {
    /**
     * Validates the ingest request.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId cannot be null or empty");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text cannot be null or empty");
        }
        if (text.length() < 10) {
            throw new IllegalArgumentException("text is too short (minimum 10 characters)");
        }
    }
}
