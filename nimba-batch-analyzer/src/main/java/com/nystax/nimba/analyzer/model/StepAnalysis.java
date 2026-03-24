package com.nystax.nimba.analyzer.model;

import java.util.ArrayList;
import java.util.List;

public class StepAnalysis {

    private StepDefinition stepDefinition;
    private String fileFormat;
    private boolean hasDeserializer;
    private String deserializerClassName;
    private LlmInsight deserializerInsight;
    private boolean deserializerMakesFunctionCalls;
    private List<FunctionCallInfo> deserializerFunctionCalls = new ArrayList<>();
    private String processorClassName;
    private LlmInsight processorInsight;
    private boolean processorMakesFunctionCalls;
    private List<FunctionCallInfo> processorFunctionCalls = new ArrayList<>();
    private String processErrorThreshold;
    private LlmInsight readerInsight;

    public StepDefinition getStepDefinition() { return stepDefinition; }
    public void setStepDefinition(StepDefinition stepDefinition) { this.stepDefinition = stepDefinition; }
    public String getFileFormat() { return fileFormat; }
    public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }
    public boolean isHasDeserializer() { return hasDeserializer; }
    public void setHasDeserializer(boolean hasDeserializer) { this.hasDeserializer = hasDeserializer; }
    public String getDeserializerClassName() { return deserializerClassName; }
    public void setDeserializerClassName(String deserializerClassName) { this.deserializerClassName = deserializerClassName; }
    public LlmInsight getDeserializerInsight() { return deserializerInsight; }
    public void setDeserializerInsight(LlmInsight deserializerInsight) { this.deserializerInsight = deserializerInsight; }
    public boolean isDeserializerMakesFunctionCalls() { return deserializerMakesFunctionCalls; }
    public void setDeserializerMakesFunctionCalls(boolean v) { this.deserializerMakesFunctionCalls = v; }
    public List<FunctionCallInfo> getDeserializerFunctionCalls() { return deserializerFunctionCalls; }
    public void setDeserializerFunctionCalls(List<FunctionCallInfo> v) { this.deserializerFunctionCalls = v; }
    public String getProcessorClassName() { return processorClassName; }
    public void setProcessorClassName(String processorClassName) { this.processorClassName = processorClassName; }
    public LlmInsight getProcessorInsight() { return processorInsight; }
    public void setProcessorInsight(LlmInsight processorInsight) { this.processorInsight = processorInsight; }
    public boolean isProcessorMakesFunctionCalls() { return processorMakesFunctionCalls; }
    public void setProcessorMakesFunctionCalls(boolean v) { this.processorMakesFunctionCalls = v; }
    public List<FunctionCallInfo> getProcessorFunctionCalls() { return processorFunctionCalls; }
    public void setProcessorFunctionCalls(List<FunctionCallInfo> v) { this.processorFunctionCalls = v; }
    public String getProcessErrorThreshold() { return processErrorThreshold; }
    public void setProcessErrorThreshold(String processErrorThreshold) { this.processErrorThreshold = processErrorThreshold; }
    public LlmInsight getReaderInsight() { return readerInsight; }
    public void setReaderInsight(LlmInsight readerInsight) { this.readerInsight = readerInsight; }
}
