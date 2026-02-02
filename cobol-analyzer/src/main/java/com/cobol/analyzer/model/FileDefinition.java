package com.cobol.analyzer.model;

public class FileDefinition {
    private String logicalName;
    private String physicalName;
    private String organization;
    private String accessMode;
    private String recordKey;
    private String fileStatus;

    public FileDefinition() {}

    public FileDefinition(String logicalName, String physicalName) {
        this.logicalName = logicalName;
        this.physicalName = physicalName;
    }

    public String getLogicalName() { return logicalName; }
    public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
    public String getPhysicalName() { return physicalName; }
    public void setPhysicalName(String physicalName) { this.physicalName = physicalName; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }
    public String getRecordKey() { return recordKey; }
    public void setRecordKey(String recordKey) { this.recordKey = recordKey; }
    public String getFileStatus() { return fileStatus; }
    public void setFileStatus(String fileStatus) { this.fileStatus = fileStatus; }
}
