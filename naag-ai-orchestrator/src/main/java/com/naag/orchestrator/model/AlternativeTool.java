package com.naag.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlternativeTool {
    private String toolName;
    private double confidence;
    private String reasoning;
}
