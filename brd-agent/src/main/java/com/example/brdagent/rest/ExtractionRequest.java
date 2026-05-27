package com.example.brdagent.rest;

import java.util.List;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

// PROVENANCE: JAX-RS resources — POST /extract request body

/**
 * Request body for {@code POST /extract}.
 */
public class ExtractionRequest {

    @NotBlank
    @JsonbProperty("unitName")
    private String unitName;

    @NotEmpty
    @JsonbProperty("sourcePaths")
    private List<String> sourcePaths;

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    public void setSourcePaths(List<String> sourcePaths) {
        this.sourcePaths = sourcePaths;
    }
}
