package com.example.conjur.admin.model;

import java.util.List;

public class RootPolicySetup {
    private String orgName;
    private List<String> extraBranches;

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public List<String> getExtraBranches() { return extraBranches; }
    public void setExtraBranches(List<String> extraBranches) { this.extraBranches = extraBranches; }
}
