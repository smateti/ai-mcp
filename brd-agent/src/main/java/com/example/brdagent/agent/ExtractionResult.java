package com.example.brdagent.agent;

import jakarta.json.bind.annotation.JsonbProperty;

// PROVENANCE: agent loop steps 6-7 — success partial or failure record

/**
 * Result of extracting a partial BRD from a single source file.
 * Either successful (with a partial BRD) or failed (with an error message).
 */
public class ExtractionResult {

    @JsonbProperty("source_file")
    private String sourceFile;

    private String status;

    @JsonbProperty("partial_brd")
    private PartialBrd partialBrd;

    @JsonbProperty("error_message")
    private String errorMessage;

    ExtractionResult() {
    }

    private ExtractionResult(String sourceFile, String status, PartialBrd partialBrd, String errorMessage) {
        this.sourceFile = sourceFile;
        this.status = status;
        this.partialBrd = partialBrd;
        this.errorMessage = errorMessage;
    }

    public static ExtractionResult success(String sourceFile, PartialBrd partialBrd) {
        return new ExtractionResult(sourceFile, "SUCCESS", partialBrd, null);
    }

    public static ExtractionResult failure(String sourceFile, String errorMessage) {
        return new ExtractionResult(sourceFile, "FAILURE", null, errorMessage);
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public PartialBrd getPartialBrd() {
        return partialBrd;
    }

    public void setPartialBrd(PartialBrd partialBrd) {
        this.partialBrd = partialBrd;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
