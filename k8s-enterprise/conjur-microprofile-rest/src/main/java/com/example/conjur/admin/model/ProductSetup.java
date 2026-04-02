package com.example.conjur.admin.model;

import java.util.List;

public class ProductSetup {
    private String orgName;
    private String environment;
    private String productName;
    private List<String> appTypes;
    private List<String> resourceTypes;

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public List<String> getAppTypes() { return appTypes; }
    public void setAppTypes(List<String> appTypes) { this.appTypes = appTypes; }
    public List<String> getResourceTypes() { return resourceTypes; }
    public void setResourceTypes(List<String> resourceTypes) { this.resourceTypes = resourceTypes; }
}
