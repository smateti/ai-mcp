package com.nystax.nimba.analyzer.model;

public class LlmInsight {

    private String className;
    private String summary;
    private String parsingLogic;
    private String businessLogic;
    private String functionCalls;
    private String errorHandling;
    private String patterns;
    private String issues;

    // Deserializer-specific
    private String fieldMapping;
    private String recordStructure;
    private String validationRules;

    // Reader-specific
    private String dataSource;
    private String queryPattern;
    private String connectionDetails;

    // Listener-specific
    private String onJobStart;
    private String onJobFinish;
    private String resourceManagement;

    // Processor-specific
    private String dataTransformations;
    private String dbOperations;
    private String outputDescription;
    private String conditionalLogic;

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getParsingLogic() { return parsingLogic; }
    public void setParsingLogic(String parsingLogic) { this.parsingLogic = parsingLogic; }
    public String getBusinessLogic() { return businessLogic; }
    public void setBusinessLogic(String businessLogic) { this.businessLogic = businessLogic; }
    public String getFunctionCalls() { return functionCalls; }
    public void setFunctionCalls(String functionCalls) { this.functionCalls = functionCalls; }
    public String getErrorHandling() { return errorHandling; }
    public void setErrorHandling(String errorHandling) { this.errorHandling = errorHandling; }
    public String getPatterns() { return patterns; }
    public void setPatterns(String patterns) { this.patterns = patterns; }
    public String getIssues() { return issues; }
    public void setIssues(String issues) { this.issues = issues; }

    public String getFieldMapping() { return fieldMapping; }
    public void setFieldMapping(String fieldMapping) { this.fieldMapping = fieldMapping; }
    public String getRecordStructure() { return recordStructure; }
    public void setRecordStructure(String recordStructure) { this.recordStructure = recordStructure; }
    public String getValidationRules() { return validationRules; }
    public void setValidationRules(String validationRules) { this.validationRules = validationRules; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public String getQueryPattern() { return queryPattern; }
    public void setQueryPattern(String queryPattern) { this.queryPattern = queryPattern; }
    public String getConnectionDetails() { return connectionDetails; }
    public void setConnectionDetails(String connectionDetails) { this.connectionDetails = connectionDetails; }

    public String getOnJobStart() { return onJobStart; }
    public void setOnJobStart(String onJobStart) { this.onJobStart = onJobStart; }
    public String getOnJobFinish() { return onJobFinish; }
    public void setOnJobFinish(String onJobFinish) { this.onJobFinish = onJobFinish; }
    public String getResourceManagement() { return resourceManagement; }
    public void setResourceManagement(String resourceManagement) { this.resourceManagement = resourceManagement; }

    public String getDataTransformations() { return dataTransformations; }
    public void setDataTransformations(String dataTransformations) { this.dataTransformations = dataTransformations; }
    public String getDbOperations() { return dbOperations; }
    public void setDbOperations(String dbOperations) { this.dbOperations = dbOperations; }
    public String getOutputDescription() { return outputDescription; }
    public void setOutputDescription(String outputDescription) { this.outputDescription = outputDescription; }
    public String getConditionalLogic() { return conditionalLogic; }
    public void setConditionalLogic(String conditionalLogic) { this.conditionalLogic = conditionalLogic; }
}
