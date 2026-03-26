package com.nystax.nimba.analyzer.model;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {

    private JobDefinition jobDefinition;
    private List<StepAnalysis> stepAnalyses = new ArrayList<>();
    private List<BatchExitUsage> batchExitUsages = new ArrayList<>();
    private String jobListenerClassName;
    private LlmInsight jobListenerInsight;
    private String jobSummary;

    public JobDefinition getJobDefinition() { return jobDefinition; }
    public void setJobDefinition(JobDefinition jobDefinition) { this.jobDefinition = jobDefinition; }
    public List<StepAnalysis> getStepAnalyses() { return stepAnalyses; }
    public void setStepAnalyses(List<StepAnalysis> stepAnalyses) { this.stepAnalyses = stepAnalyses; }
    public List<BatchExitUsage> getBatchExitUsages() { return batchExitUsages; }
    public void setBatchExitUsages(List<BatchExitUsage> batchExitUsages) { this.batchExitUsages = batchExitUsages; }
    public String getJobListenerClassName() { return jobListenerClassName; }
    public void setJobListenerClassName(String jobListenerClassName) { this.jobListenerClassName = jobListenerClassName; }
    public LlmInsight getJobListenerInsight() { return jobListenerInsight; }
    public void setJobListenerInsight(LlmInsight jobListenerInsight) { this.jobListenerInsight = jobListenerInsight; }
    public String getJobSummary() { return jobSummary; }
    public void setJobSummary(String jobSummary) { this.jobSummary = jobSummary; }
}
