package com.naag.rag.dto;

import java.util.List;

public record QueryResponse(
        boolean success,
        String question,
        String answer,
        List<SourceMetadata> sources,
        String errorMessage
) {
    public static QueryResponse success(String question, String answer, List<SourceMetadata> sources) {
        return new QueryResponse(true, question, answer, sources, null);
    }

    public static QueryResponse error(String question, String errorMessage) {
        return new QueryResponse(false, question, null, null, errorMessage);
    }
}
