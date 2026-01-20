package com.naag.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationResponse {
    private Intent intent;
    private String selectedTool;
    private double confidence;
    private Map<String, Object> parameters;
    private Object toolResult;
    private String response;
    private String reasoning;
    private List<AlternativeTool> alternatives;
    private boolean requiresConfirmation;
    private boolean requiresParameters;
    private List<String> missingParameters;
}
