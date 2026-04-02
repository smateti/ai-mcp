package com.example.conjur.admin.model;

import java.util.List;

public class EnvironmentSetup {
    private String orgName;
    private List<String> environments;

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public List<String> getEnvironments() { return environments; }
    public void setEnvironments(List<String> environments) { this.environments = environments; }
}
