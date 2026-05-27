package com.example.brdagent.rest;

import java.util.List;

import jakarta.json.bind.annotation.JsonbProperty;

import com.example.brdagent.agent.ExtractionResult;

// PROVENANCE: JAX-RS resources — POST /extract response body

/**
 * Response body for {@code POST /extract}. Contains per-file results
 * and a summary.
 */
public class ExtractionResponse {

    @JsonbProperty("unit_name")
    private String unitName;

    @JsonbProperty("files_processed")
    private int filesProcessed;

    @JsonbProperty("files_succeeded")
    private int filesSucceeded;

    @JsonbProperty("files_failed")
    private int filesFailed;

    private List<ExtractionResult> results;

    ExtractionResponse() {
    }

    public ExtractionResponse(String unitName, List<ExtractionResult> results) {
        this.unitName = unitName;
        this.results = results;
        this.filesProcessed = results.size();
        this.filesSucceeded = (int) results.stream().filter(ExtractionResult::isSuccess).count();
        this.filesFailed = filesProcessed - filesSucceeded;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public int getFilesProcessed() {
        return filesProcessed;
    }

    public void setFilesProcessed(int filesProcessed) {
        this.filesProcessed = filesProcessed;
    }

    public int getFilesSucceeded() {
        return filesSucceeded;
    }

    public void setFilesSucceeded(int filesSucceeded) {
        this.filesSucceeded = filesSucceeded;
    }

    public int getFilesFailed() {
        return filesFailed;
    }

    public void setFilesFailed(int filesFailed) {
        this.filesFailed = filesFailed;
    }

    public List<ExtractionResult> getResults() {
        return results;
    }

    public void setResults(List<ExtractionResult> results) {
        this.results = results;
    }
}
