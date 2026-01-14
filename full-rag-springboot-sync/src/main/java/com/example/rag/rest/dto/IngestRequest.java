package com.example.rag.rest.dto;

import java.util.List;

/**
 * Request DTO for document ingestion endpoint.
 * Contains the document ID, full text content, and optional categories to be ingested into the RAG system.
 */
public record IngestRequest(
    String docId,
    String text,
    List<String> categories  // Optional: Categories this document belongs to
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

    /**
     * Gets categories or returns empty list if null.
     */
    public List<String> getCategoriesOrEmpty() {
        return categories != null ? categories : List.of();
    }
}
