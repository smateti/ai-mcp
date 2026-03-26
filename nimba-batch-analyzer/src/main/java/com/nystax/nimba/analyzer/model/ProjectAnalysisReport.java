package com.nystax.nimba.analyzer.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProjectAnalysisReport {

    private String projectPath;
    private LocalDateTime analyzedAt;
    private List<WasDependency> wasDependencies = new ArrayList<>();
    private List<AnalysisResult> jobAnalyses = new ArrayList<>();

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
    public List<WasDependency> getWasDependencies() { return wasDependencies; }
    public void setWasDependencies(List<WasDependency> wasDependencies) { this.wasDependencies = wasDependencies; }
    public List<AnalysisResult> getJobAnalyses() { return jobAnalyses; }
    public void setJobAnalyses(List<AnalysisResult> jobAnalyses) { this.jobAnalyses = jobAnalyses; }
}
