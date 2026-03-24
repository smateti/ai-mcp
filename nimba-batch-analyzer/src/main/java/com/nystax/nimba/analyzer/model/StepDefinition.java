package com.nystax.nimba.analyzer.model;

public class StepDefinition {

    private String stepId;
    private String parallelism;
    private boolean failOnError = true;
    private StepType stepType;
    private ReaderDefinition reader;
    private ProcessorDefinition processor;

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public String getParallelism() { return parallelism; }
    public void setParallelism(String parallelism) { this.parallelism = parallelism; }
    public boolean isFailOnError() { return failOnError; }
    public void setFailOnError(boolean failOnError) { this.failOnError = failOnError; }
    public StepType getStepType() { return stepType; }
    public void setStepType(StepType stepType) { this.stepType = stepType; }
    public ReaderDefinition getReader() { return reader; }
    public void setReader(ReaderDefinition reader) { this.reader = reader; }
    public ProcessorDefinition getProcessor() { return processor; }
    public void setProcessor(ProcessorDefinition processor) { this.processor = processor; }
}
