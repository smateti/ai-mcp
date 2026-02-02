package com.cobol.analyzer.model;

public class ParagraphAnalysis {
    private String name;
    private String code;
    private String summary;

    public ParagraphAnalysis() {}

    public ParagraphAnalysis(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
