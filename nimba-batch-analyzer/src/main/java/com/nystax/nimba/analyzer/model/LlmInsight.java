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
}
