package com.naagi.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationRequest {
    private String message;
    private String sessionId;
    private String categoryId;
}
