package com.example.conjur.admin.model;

public class SecretEntry {

    private String variableId;
    private String value;
    private boolean found;
    private String error;

    public SecretEntry() {}

    public SecretEntry(String variableId, String value, boolean found) {
        this.variableId = variableId;
        this.value = value;
        this.found = found;
    }

    public String getVariableId() { return variableId; }
    public void setVariableId(String variableId) { this.variableId = variableId; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
