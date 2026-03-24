package com.nystax.nimba.analyzer.model;

import java.util.ArrayList;
import java.util.List;

public class JobDefinition {

    private String jobId;
    private String xmlFilePath;
    private boolean resumable;
    private boolean archiveFiles;
    private String jobListenerId;
    private List<StepDefinition> steps = new ArrayList<>();

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getXmlFilePath() { return xmlFilePath; }
    public void setXmlFilePath(String xmlFilePath) { this.xmlFilePath = xmlFilePath; }
    public boolean isResumable() { return resumable; }
    public void setResumable(boolean resumable) { this.resumable = resumable; }
    public boolean isArchiveFiles() { return archiveFiles; }
    public void setArchiveFiles(boolean archiveFiles) { this.archiveFiles = archiveFiles; }
    public String getJobListenerId() { return jobListenerId; }
    public void setJobListenerId(String jobListenerId) { this.jobListenerId = jobListenerId; }
    public List<StepDefinition> getSteps() { return steps; }
    public void setSteps(List<StepDefinition> steps) { this.steps = steps; }
}
