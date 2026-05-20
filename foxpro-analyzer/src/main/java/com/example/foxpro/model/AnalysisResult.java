package com.example.foxpro.model;

/**
 * Holds the LLM-generated summary for a code unit.
 */
public class AnalysisResult {

    private final String sourceFile;
    private final String unitName;
    private final String unitType; // "MODULE", "PROCEDURE", "FUNCTION"
    private final String summary;

    public AnalysisResult(String sourceFile, String unitName, String unitType, String summary) {
        this.sourceFile = sourceFile;
        this.unitName = unitName;
        this.unitType = unitType;
        this.summary = summary;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getUnitName() {
        return unitName;
    }

    public String getUnitType() {
        return unitType;
    }

    public String getSummary() {
        return summary;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s::%s\n%s", unitType, sourceFile, unitName, summary);
    }
}
