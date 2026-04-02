package com.example.conjur.admin.model;

public class JwtAuthenticatorSetup {
    private String serviceId;
    private String jwksUri;
    private String tokenAppProperty;
    private String identityPath;
    private String issuer;
    private String audience;

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }
    public String getTokenAppProperty() { return tokenAppProperty; }
    public void setTokenAppProperty(String tokenAppProperty) { this.tokenAppProperty = tokenAppProperty; }
    public String getIdentityPath() { return identityPath; }
    public void setIdentityPath(String identityPath) { this.identityPath = identityPath; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
}
