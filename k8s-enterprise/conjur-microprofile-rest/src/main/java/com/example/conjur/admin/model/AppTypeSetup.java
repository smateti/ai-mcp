package com.example.conjur.admin.model;

public class AppTypeSetup {
    private String orgName;
    private String environment;
    private String product;
    private String appType;

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }
}
