package com.naagi.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolSelectionResult {
    private String selectedTool;
    private double confidence;
    private Map<String, Object> extractedParameters;
    private String reasoning;
    private List<AlternativeTool> alternatives;
}
