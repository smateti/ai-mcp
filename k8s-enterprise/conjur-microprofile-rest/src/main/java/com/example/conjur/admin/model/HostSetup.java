package com.example.conjur.admin.model;

public class HostSetup {
    private String orgName;
    private String environment;
    private String product;
    private String appType;
    private String hostId;
    private String namespace;
    private String serviceAccount;

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getServiceAccount() { return serviceAccount; }
    public void setServiceAccount(String serviceAccount) { this.serviceAccount = serviceAccount; }
}
