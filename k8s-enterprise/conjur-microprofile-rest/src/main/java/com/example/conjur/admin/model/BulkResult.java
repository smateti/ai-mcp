package com.example.conjur.admin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BulkResult {

    private int registered;
    private List<Map<String, Object>> results = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public int getRegistered() { return registered; }
    public void setRegistered(int registered) { this.registered = registered; }

    public List<Map<String, Object>> getResults() { return results; }
    public void setResults(List<Map<String, Object>> results) { this.results = results; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public void addResult(Map<String, Object> result) {
        this.results.add(result);
        this.registered = this.results.size();
    }

    public void addError(String error) {
        this.errors.add(error);
    }
}
