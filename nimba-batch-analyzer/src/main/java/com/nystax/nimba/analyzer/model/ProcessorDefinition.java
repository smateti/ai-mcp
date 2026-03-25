package com.nystax.nimba.analyzer.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProcessorDefinition {

    private String processorId;
    private String source; // e.g. "step.managedStep.out" — reads from another step's output/input
    private Map<String, String> parameters = new LinkedHashMap<>();

    public ProcessorDefinition() {}

    public ProcessorDefinition(String processorId, Map<String, String> parameters) {
        this.processorId = processorId;
        this.parameters = parameters;
    }

    public String getProcessorId() { return processorId; }
    public void setProcessorId(String processorId) { this.processorId = processorId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }
}
