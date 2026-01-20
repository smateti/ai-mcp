package com.naag.categoryadmin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagDocument {
    private String docId;  // Maps to docId from RAG service
    private String title;
    private String content;
    private String categoryId;
    private Integer chunkCount;  // Number of chunks in RAG
    private Map<String, Object> metadata;
    private LocalDateTime ingestedAt;
}
