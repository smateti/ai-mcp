package com.example.rag.rest.dto;

/**
 * Metadata about a source chunk retrieved during RAG query.
 * Contains document ID, chunk index, relevance score, and the actual text.
 */
public record SourceMetadata(
    String docId,
    int chunkIndex,
    double relevanceScore,
    String text
) {
    /**
     * Creates a truncated version of the text for display (max 200 chars).
     */
    public String textPreview() {
        if (text == null) {
            return "";
        }
        if (text.length() <= 200) {
            return text;
        }
        return text.substring(0, 197) + "...";
    }
}
