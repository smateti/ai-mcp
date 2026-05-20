package com.example.foxpro.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed FoxPro source file with its logical units (procedures/functions).
 */
public class FoxProModule {

    private final String fileName;
    private final String filePath;
    private String headerComment;
    private String mainCode;
    private final List<FoxProProcedure> procedures;

    public FoxProModule(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.procedures = new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getHeaderComment() {
        return headerComment;
    }

    public void setHeaderComment(String headerComment) {
        this.headerComment = headerComment;
    }

    public String getMainCode() {
        return mainCode;
    }

    public void setMainCode(String mainCode) {
        this.mainCode = mainCode;
    }

    public List<FoxProProcedure> getProcedures() {
        return procedures;
    }

    public void addProcedure(FoxProProcedure procedure) {
        this.procedures.add(procedure);
    }

    @Override
    public String toString() {
        return "FoxProModule{" +
                "fileName='" + fileName + '\'' +
                ", procedures=" + procedures.size() +
                '}';
    }
}
