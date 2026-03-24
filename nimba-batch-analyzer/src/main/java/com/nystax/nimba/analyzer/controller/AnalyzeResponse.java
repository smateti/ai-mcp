package com.nystax.nimba.analyzer.controller;

import com.nystax.nimba.analyzer.model.ProjectAnalysisReport;

public class AnalyzeResponse {

    private boolean success;
    private ProjectAnalysisReport report;
    private String reportPath;
    private String error;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public ProjectAnalysisReport getReport() { return report; }
    public void setReport(ProjectAnalysisReport report) { this.report = report; }
    public String getReportPath() { return reportPath; }
    public void setReportPath(String reportPath) { this.reportPath = reportPath; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
