package com.nystax.nimba.analyzer.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ReaderDefinition {

    private String readerId;
    private ReaderType readerType;
    private Map<String, String> parameters = new LinkedHashMap<>();

    public ReaderDefinition() {}

    public ReaderDefinition(String readerId, ReaderType readerType, Map<String, String> parameters) {
        this.readerId = readerId;
        this.readerType = readerType;
        this.parameters = parameters;
    }

    public String getReaderId() { return readerId; }
    public void setReaderId(String readerId) { this.readerId = readerId; }
    public ReaderType getReaderType() { return readerType; }
    public void setReaderType(ReaderType readerType) { this.readerType = readerType; }
    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }
}
