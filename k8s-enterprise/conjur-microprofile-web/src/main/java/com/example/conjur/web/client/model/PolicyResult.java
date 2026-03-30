package com.example.conjur.web.client.model;

import java.util.List;

public class PolicyResult {

    private String message;
    private String branch;
    private List<String> createdVariables;
    private String rawResponse;
    private String error;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public List<String> getCreatedVariables() { return createdVariables; }
    public void setCreatedVariables(List<String> createdVariables) { this.createdVariables = createdVariables; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
