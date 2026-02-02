package com.cobol.analyzer.model;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {
    private String fileName;
    private String programId;
    private String overallSummary;
    private String purpose;
    private List<String> dataStructures = new ArrayList<>();
    private List<String> fileOperations = new ArrayList<>();
    private List<String> paragraphs = new ArrayList<>();
    private List<ParagraphAnalysis> paragraphDetails = new ArrayList<>();
    private String rawCode;
    private long analysisTimeMs;

    // DB2
    private List<String> db2Tables = new ArrayList<>();
    private List<String> db2Cursors = new ArrayList<>();
    private List<String> db2Operations = new ArrayList<>();

    // IDMS
    private List<String> idmsSchemas = new ArrayList<>();
    private List<String> idmsRecords = new ArrayList<>();
    private List<String> idmsOperations = new ArrayList<>();

    // CICS
    private List<String> cicsOperations = new ArrayList<>();

    // Copybooks
    private List<String> copybooks = new ArrayList<>();

    // Called programs
    private List<String> calledPrograms = new ArrayList<>();

    // File definitions (SELECT/FD)
    private List<FileDefinition> fileDefinitions = new ArrayList<>();

    // Program type
    private String programType;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getProgramId() { return programId; }
    public void setProgramId(String programId) { this.programId = programId; }
    public String getOverallSummary() { return overallSummary; }
    public void setOverallSummary(String overallSummary) { this.overallSummary = overallSummary; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public List<String> getDataStructures() { return dataStructures; }
    public void setDataStructures(List<String> dataStructures) { this.dataStructures = dataStructures; }
    public List<String> getFileOperations() { return fileOperations; }
    public void setFileOperations(List<String> fileOperations) { this.fileOperations = fileOperations; }
    public List<String> getParagraphs() { return paragraphs; }
    public void setParagraphs(List<String> paragraphs) { this.paragraphs = paragraphs; }
    public List<ParagraphAnalysis> getParagraphDetails() { return paragraphDetails; }
    public void setParagraphDetails(List<ParagraphAnalysis> paragraphDetails) { this.paragraphDetails = paragraphDetails; }
    public String getRawCode() { return rawCode; }
    public void setRawCode(String rawCode) { this.rawCode = rawCode; }
    public long getAnalysisTimeMs() { return analysisTimeMs; }
    public void setAnalysisTimeMs(long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }
    public List<String> getDb2Tables() { return db2Tables; }
    public void setDb2Tables(List<String> db2Tables) { this.db2Tables = db2Tables; }
    public List<String> getDb2Cursors() { return db2Cursors; }
    public void setDb2Cursors(List<String> db2Cursors) { this.db2Cursors = db2Cursors; }
    public List<String> getDb2Operations() { return db2Operations; }
    public void setDb2Operations(List<String> db2Operations) { this.db2Operations = db2Operations; }
    public List<String> getIdmsSchemas() { return idmsSchemas; }
    public void setIdmsSchemas(List<String> idmsSchemas) { this.idmsSchemas = idmsSchemas; }
    public List<String> getIdmsRecords() { return idmsRecords; }
    public void setIdmsRecords(List<String> idmsRecords) { this.idmsRecords = idmsRecords; }
    public List<String> getIdmsOperations() { return idmsOperations; }
    public void setIdmsOperations(List<String> idmsOperations) { this.idmsOperations = idmsOperations; }
    public List<String> getCicsOperations() { return cicsOperations; }
    public void setCicsOperations(List<String> cicsOperations) { this.cicsOperations = cicsOperations; }
    public List<String> getCopybooks() { return copybooks; }
    public void setCopybooks(List<String> copybooks) { this.copybooks = copybooks; }
    public List<String> getCalledPrograms() { return calledPrograms; }
    public void setCalledPrograms(List<String> calledPrograms) { this.calledPrograms = calledPrograms; }
    public List<FileDefinition> getFileDefinitions() { return fileDefinitions; }
    public void setFileDefinitions(List<FileDefinition> fileDefinitions) { this.fileDefinitions = fileDefinitions; }
    public String getProgramType() { return programType; }
    public void setProgramType(String programType) { this.programType = programType; }
}
