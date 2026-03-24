package com.nystax.nimba.analyzer.model;

public class FunctionCallInfo {

    private String clientClassName;
    private String methodCalled;
    private String callingClass;
    private int lineNumber;

    public FunctionCallInfo() {}

    public FunctionCallInfo(String clientClassName, String methodCalled, String callingClass, int lineNumber) {
        this.clientClassName = clientClassName;
        this.methodCalled = methodCalled;
        this.callingClass = callingClass;
        this.lineNumber = lineNumber;
    }

    public String getClientClassName() { return clientClassName; }
    public void setClientClassName(String clientClassName) { this.clientClassName = clientClassName; }
    public String getMethodCalled() { return methodCalled; }
    public void setMethodCalled(String methodCalled) { this.methodCalled = methodCalled; }
    public String getCallingClass() { return callingClass; }
    public void setCallingClass(String callingClass) { this.callingClass = callingClass; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
}
