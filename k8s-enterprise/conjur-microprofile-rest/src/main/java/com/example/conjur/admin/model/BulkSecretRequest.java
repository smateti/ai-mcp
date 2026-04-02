package com.example.conjur.admin.model;

import java.util.List;

public class BulkSecretRequest {
    private List<SecretValue> secrets;

    public List<SecretValue> getSecrets() { return secrets; }
    public void setSecrets(List<SecretValue> secrets) { this.secrets = secrets; }

    public static class SecretValue {
        private String variableId;
        private String value;

        public String getVariableId() { return variableId; }
        public void setVariableId(String variableId) { this.variableId = variableId; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
