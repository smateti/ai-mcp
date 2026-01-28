package com.naagi.rag.dto;

public record SourceMetadata(
        String docId,
        int chunkIndex,
        double relevanceScore,
        String text,
        String title
) {}
