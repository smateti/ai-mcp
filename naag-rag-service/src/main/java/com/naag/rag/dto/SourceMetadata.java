package com.naag.rag.dto;

public record SourceMetadata(
        String docId,
        int chunkIndex,
        double relevanceScore,
        String text
) {}
