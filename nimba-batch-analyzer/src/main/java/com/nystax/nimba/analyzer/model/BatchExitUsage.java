package com.nystax.nimba.analyzer.model;

public class BatchExitUsage {

    private String className;
    private int lineNumber;
    private String statusCode;
    private String message;

    public BatchExitUsage() {}

    public BatchExitUsage(String className, int lineNumber, String statusCode, String message) {
        this.className = className;
        this.lineNumber = lineNumber;
        this.statusCode = statusCode;
        this.message = message;
    }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
