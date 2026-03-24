package com.nystax.nimba.analyzer.controller;

import com.nystax.nimba.analyzer.model.ProjectAnalysisReport;

public class AnalyzeResponse {

    private boolean success;
    private ProjectAnalysisReport report;
    private String analysisReportPath;
    private String summaryDocumentPath;
    private String error;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public ProjectAnalysisReport getReport() { return report; }
    public void setReport(ProjectAnalysisReport report) { this.report = report; }
    public String getAnalysisReportPath() { return analysisReportPath; }
    public void setAnalysisReportPath(String analysisReportPath) { this.analysisReportPath = analysisReportPath; }
    public String getSummaryDocumentPath() { return summaryDocumentPath; }
    public void setSummaryDocumentPath(String summaryDocumentPath) { this.summaryDocumentPath = summaryDocumentPath; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
