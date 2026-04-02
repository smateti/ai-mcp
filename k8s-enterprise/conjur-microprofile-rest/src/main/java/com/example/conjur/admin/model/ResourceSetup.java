package com.example.conjur.admin.model;

import java.util.Map;

public class ResourceSetup {
    private String orgName;
    private String environment;
    private String product;
    private String resourceType;
    private String resourceName;
    private Map<String, String> initialSecrets;

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public Map<String, String> getInitialSecrets() { return initialSecrets; }
    public void setInitialSecrets(Map<String, String> initialSecrets) { this.initialSecrets = initialSecrets; }
}
