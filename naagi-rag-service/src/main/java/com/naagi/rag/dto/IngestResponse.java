package com.naagi.rag.dto;

public record IngestResponse(
        boolean success,
        String docId,
        int chunksCreated,
        String message
) {
    public static IngestResponse success(String docId, int chunksCreated) {
        return new IngestResponse(true, docId, chunksCreated, "Document ingested successfully");
    }

    public static IngestResponse error(String docId, String message) {
        return new IngestResponse(false, docId, 0, message);
    }
}
