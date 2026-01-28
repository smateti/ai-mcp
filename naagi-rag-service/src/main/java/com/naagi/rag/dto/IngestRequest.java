package com.naagi.rag.dto;

import java.util.List;

public record IngestRequest(
        String docId,
        String text,
        List<String> categories
) {
    public void validate() {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId is required");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
    }

    public List<String> getCategoriesOrEmpty() {
        return categories != null ? categories : List.of();
    }
}
